/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;

import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Pair;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton class for managing AuthN and AuthZ session.
 */
public class SessionManager {
    private static final Logger logger = LogManager.getLogger(SessionManager.class);
    private static final String SESSION_ID = "SessionId";
    private static final String INTERNAL_SESSION_ID = "InternalSessionId";

    // There are two types of Session IDs defined here. Internal session IDs
    // are derived by hashing device credentials. This is done so that we can
    // avoid creating multiple sessions for the same device. Only a single session
    // for a given set of device credentials is allowed at a given time.
    // However, Session IDs should not be predictable. Here, we create a separate
    // external Session ID which can be returned to the IPC client. The external
    // Session ID is stored along with the Session itself so that we can clean up
    // duplicate external Session IDs
    @Getter(AccessLevel.PACKAGE)
    private final Map<String, Pair<String,Session>> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, String> externalToInternalSessionMap = new ConcurrentHashMap<>();

    /**
     * find session by id.
     *
     * @param sessionId session identifier
     * @return session or null
     */
    public Session findSession(String sessionId) {
        String internalSessionId = externalToInternalSessionMap.get(sessionId);
        if (internalSessionId == null) {
            return null;
        }
        Pair<String, Session> sessionPair = sessionMap.get(internalSessionId);
        if (sessionPair != null) {
            return sessionPair.getRight();
        }
        return null;
    }

    /**
     * Create session with certificate.
     *
     * @deprecated Sessions should be created using device credentials instead of certificates
     * @param certificate Client device certificate
     * @return session id
     */
    @Deprecated public String createSession(Certificate certificate) {
        Session session = new SessionImpl(certificate);
        return addSessionInternal(session, certificate);
    }

    /**
     * Create session with device credentials.
     *
     * @param credentialType Device credential type
     * @param credentialMap  Device credential map
     * @return session id
     * @throws AuthenticationException if device credentials were not able to be validated
     */
    public String createSession(String credentialType, Map<String, String> credentialMap)
            throws AuthenticationException {
        Session session = SessionCreator.createSession(credentialType, credentialMap);
        return addSessionInternal(session, credentialMap);
    }

    /**
     * close the session by id.
     *
     * @param sessionId session identifier
     */
    public void closeSession(String sessionId) {
        logger.atDebug().kv(SESSION_ID, sessionId).log("Closing session");
        closeSessionInternal(sessionId);
    }

    private synchronized void closeSessionInternal(String externalId) {
        String internalSessionId = externalToInternalSessionMap.get(externalId);
        if (internalSessionId == null) {
            // Session has already been evicted
            return;
        }
        externalToInternalSessionMap.remove(externalId);
        sessionMap.remove(internalSessionId);
    }

    /*
     * Returns external session ID
     */
    private synchronized String addSessionInternal(Session session, Object credentials) {
        String internalId = generateInternalSessionId(credentials);
        String externalId = generateExternalSessionId();
        logger.atDebug().kv(SESSION_ID, externalId).kv(INTERNAL_SESSION_ID, internalId).log("Creating new session");

        Pair<String, Session> existingPair = sessionMap.put(internalId, new Pair<>(externalId, session));
        externalToInternalSessionMap.put(externalId, internalId);
        if (existingPair != null) {
            externalToInternalSessionMap.remove(existingPair.getLeft());
            logger.atDebug().kv(SESSION_ID, existingPair.getLeft()).log("Evicting previous session");
        }
        return externalId;
    }

    String generateInternalSessionId(Object credentials) {
        // TODO: Replace with a better hashing mechanism to avoid unnecessary collisions
        return String.valueOf(credentials.hashCode());
    }

    String generateExternalSessionId() {
        String externalId;
        do {
            externalId = UUID.randomUUID().toString();
        } while (externalToInternalSessionMap.containsKey(externalId));
        return externalId;
    }

}
