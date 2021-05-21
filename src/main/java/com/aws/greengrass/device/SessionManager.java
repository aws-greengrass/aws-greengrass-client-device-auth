/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.device.attribute.AttributeProvider;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.iot.Thing;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.inject.Inject;

/**
 * Singleton class for managing AuthN and AuthZ session.
 */
public class SessionManager {
    private static final Logger logger = LogManager.getLogger(SessionManager.class);
    private static final long SESSION_CHECK_DELAY_IN_DAYS = 7L;
    private static final TimeUnit SESSION_CHECK_TIME_UNIT = TimeUnit.DAYS;
    private static final long SESSION_EXPIRY_CHECK_DELAY_IN_MINUTES = 20L;
    private static final TimeUnit SESSION_EXPIRY_CHECK_TIME_UNIT = TimeUnit.MINUTES;
    private static final long ONE_HOUR_IN_MILLISECONDS = 60 * 60 * 1000L;
    public static final String SESSION_ID = "sessionId";

    private final IotAuthClient iotAuthClient;
    private final CertificateStore certificateStore;
    private final ScheduledExecutorService scheduledExecutorService;

    @Setter(AccessLevel.PACKAGE)
    private Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    @Setter(AccessLevel.PACKAGE)
    private ScheduledFuture<?> sessionRefreshFuture;

    @Setter(AccessLevel.PACKAGE)
    private ScheduledFuture<?> sessionExpiryCheckFuture;

    /**
     * Session Manager constructor.
     *
     * @param iotAuthClient            cloud service client
     * @param certificateStore         certificate store
     * @param scheduledExecutorService scheduling service
     */
    @Inject
    public SessionManager(IotAuthClient iotAuthClient, CertificateStore certificateStore,
                          ScheduledExecutorService scheduledExecutorService) {
        this.iotAuthClient = iotAuthClient;
        this.certificateStore = certificateStore;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    /**
     * find session by id.
     *
     * @param sessionId session identifier
     * @return session or null
     */
    public Session findSession(String sessionId) {
        return sessionMap.computeIfPresent(sessionId, (k, v) -> {
            // extend session expiry of cache hit
            SessionDecorator session = (SessionDecorator) v;
            session.setLastVisit(Instant.now());
            return session;
        });
    }

    /**
     * create session with certificate.
     *
     * @param certificate certificate stored in session
     * @return session id
     */
    public String createSession(Certificate certificate) {
        String sessionId = generateSessionId();
        sessionMap.put(sessionId, new SessionDecorator(sessionId, new SessionImpl(certificate), Instant.now()));
        logger.atInfo().kv(SESSION_ID, sessionId).log("Created the session");
        return sessionId;
    }

    private String generateSessionId() {
        //TODO better session id format?
        return UUID.randomUUID().toString();
    }

    /**
     * close the session by id.
     *
     * @param sessionId session identifier
     * @throws AuthorizationException if no session associated with sessionId
     */
    public void closeSession(String sessionId) throws AuthorizationException {
        logger.atInfo().kv(SESSION_ID, sessionId).log("Closing the session");
        Session session = sessionMap.get(sessionId);
        if (!sessionMap.remove(sessionId, session)) {
            throw new AuthorizationException(String.format("No session is associated with session id (%s)", sessionId));
        }
    }

    private void closeSession(Session session, String reason) {
        SessionDecorator sessionDecorator = (SessionDecorator) session;
        logger.atInfo().kv("session", sessionDecorator.getSession())
                .log(String.format("Close the session because %s", reason));
        try {
            closeSession(sessionDecorator.getSessionId());
        } catch (AuthorizationException e) {
            logger.atInfo().cause(e).kv(SESSION_ID, sessionDecorator.getSessionId()).log(e.getMessage());
        }
    }

    /**
     * Start session healthy check.
     */
    public void startSessionCheck() {
        sessionRefreshFuture = scheduledExecutorService
                .scheduleWithFixedDelay(this::refreshSessions, 0, SESSION_CHECK_DELAY_IN_DAYS, SESSION_CHECK_TIME_UNIT);
    }

    /**
     * Stop session healthy check.
     */
    public void stopSessionCheck() {
        if (sessionRefreshFuture != null) {
            sessionRefreshFuture.cancel(true);
        }
    }

    /**
     * refresh the session, close the session if certificate or thing is invalid.
     */
    void refreshSessions() {
        Map<String, List<Session>> certificateToSessionsMap = getCertificateToSessionsMap();
        for (Map.Entry<String, List<Session>> entry : certificateToSessionsMap.entrySet()) {
            if (shouldCloseSessionsForCertificate(entry.getKey())) {
                // close all sessions with this certificate
                for (Session session : entry.getValue()) {
                    closeSession(session, "certificate is not valid");
                }
            } else {
                for (Session session : entry.getValue()) {
                    if (shouldCloseSessionForThing(session)) {
                        // thing no other attribute matching supported than thingName, close the session
                        // TODO detach the thing from the session
                        closeSession(session, "thing is not associated with certificate");
                    }
                }
            }
        }
    }

    private Map<String, List<Session>> getCertificateToSessionsMap() {
        Map<String, List<Session>> certificateToSessionsMap = new HashMap<>();
        for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
            Certificate certificate = (Certificate) entry.getValue().getAttributeProvider(Certificate.NAMESPACE);
            List<Session> sessions = certificateToSessionsMap
                    .computeIfAbsent(certificate.getCertificateHash(), (k) -> new ArrayList<>());
            sessions.add(entry.getValue());
        }
        return certificateToSessionsMap;
    }

    private boolean shouldCloseSessionsForCertificate(String certificatePemHash) {
        String certificatePem;
        try {
            certificatePem = certificateStore.loadDeviceCertificate(certificatePemHash);
        } catch (IOException e) {
            logger.atError().cause(e).kv("certificatePemHash", certificatePemHash)
                    .log("Failed to load certificate saved on disk");
            return true;
        }

        if (!certificatePemHash.equals(CertificateStore.computeCertificatePemHash(certificatePem))) {
            logger.atError().kv("certificatePemHash", certificatePemHash).log("certificate got modified.");
            return true;
        }

        try {
            Optional<String> certificateId = iotAuthClient.getActiveCertificateId(certificatePem);
            return !certificateId.isPresent();
        } catch (CloudServiceInteractionException e) {
            logger.atError().cause(e).kv("certificatePem", certificatePem).log("Can't verify client certificate");
        }
        return false;
    }

    private boolean shouldCloseSessionForThing(Session session) {
        Thing thing = (Thing) session.getAttributeProvider(Thing.NAMESPACE);
        if (thing != null) {
            Certificate certificate = (Certificate) session.getAttributeProvider(Certificate.NAMESPACE);
            try {
                return !iotAuthClient.isThingAttachedToCertificate(thing, certificate);
            } catch (CloudServiceInteractionException e) {
                // In case of cloud service availability issue, log the error but assume valid till next check
                logger.atError().cause(e).kv("thingName", thing.getThingName())
                        .kv("certificateId", certificate.getIotCertificateId())
                        .log("Can't verify thing certificate association");
            }
        }
        return false;
    }

    /**
     * Start session expiry check.
     */
    public void startSessionExpiryCheck() {
        sessionExpiryCheckFuture = scheduledExecutorService
                .scheduleWithFixedDelay(this::checkSessionExpiry, 0, SESSION_EXPIRY_CHECK_DELAY_IN_MINUTES,
                        SESSION_EXPIRY_CHECK_TIME_UNIT);
    }

    /**
     * Stop session expiry check.
     */
    public void stopSessionExpiryCheck() {
        if (sessionExpiryCheckFuture != null) {
            sessionExpiryCheckFuture.cancel(true);
        }
    }

    void checkSessionExpiry() {
        Instant now = Instant.now();
        sessionMap.entrySet().removeIf(entry -> {
            SessionDecorator session = (SessionDecorator) entry.getValue();
            if (session.getLastVisit() != null && session.getLastVisit().plusMillis(ONE_HOUR_IN_MILLISECONDS)
                    .isBefore(now)) {
                logger.atDebug().kv(SESSION_ID, session.getSessionId())
                        .kv("lastVisit", session.getLastVisit())
                        .kv("maxInactiveInterval", ONE_HOUR_IN_MILLISECONDS)
                        .log("Session exceeds max inactive interval, close the session.");
                return true;
            }
            return false;
        });
    }

    @AllArgsConstructor
    @RequiredArgsConstructor
    @Getter
    static class SessionDecorator implements Session {
        private final String sessionId;
        private final Session session;
        @Setter
        private Instant lastVisit;

        @Override
        public AttributeProvider getAttributeProvider(String attributeProviderNameSpace) {
            return session.getAttributeProvider(attributeProviderNameSpace);
        }

        @Override
        public AttributeProvider putAttributeProvider(String attributeProviderNameSpace,
                                                      AttributeProvider attributeProvider) {
            return session.putAttributeProvider(attributeProviderNameSpace, attributeProvider);
        }

        @Override
        public AttributeProvider computeAttributeProviderIfAbsent(String key,
                                                                  Function<? super String, ? extends AttributeProvider>
                                                                          mappingFunction) {
            return session.computeAttributeProviderIfAbsent(key, mappingFunction);
        }

        @Override
        public DeviceAttribute getSessionAttribute(String attributeNamespace, String attributeName) {
            return session.getSessionAttribute(attributeNamespace, attributeName);
        }
    }
}
