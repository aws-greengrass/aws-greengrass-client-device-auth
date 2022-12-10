/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.session.events.SessionCreationEvent;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;

/**
 * Singleton class for managing AuthN and AuthZ sessions.
 */
public class SessionManager {
    private static final Logger logger = LogManager.getLogger(SessionManager.class);
    private static final String SESSION_ID = "SessionId";
    private final DomainEvents domainEvents;

    // Thread-safe LRU Session Cache that evicts the eldest entry (based on access order) upon reaching its size.
    // TODO: Support time-based cache eviction (Session timeout) and Session deduping.
    @Getter(AccessLevel.PACKAGE)
    private final Map<String, Session> sessionMap =
            Collections.synchronizedMap(new LinkedHashMap<String, Session>(getSessionCapacity(), 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
                    // check size against latest configured session capacity
                    if (size() > getSessionCapacity()) {
                        logger.atTrace().kv(SESSION_ID, eldest.getKey())
                                .log("Session Cache reached its capacity. Closing session.");
                        return true;
                    }
                    return false;
                }
            });

    private SessionConfig sessionConfig;

    @Inject
    public SessionManager(DomainEvents domainEvents) {
        this.domainEvents = domainEvents;
    }

    /**
     * Looks up a session by id.
     *
     * @param sessionId session identifier
     * @return session or null
     */
    public Session findSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    /**
     * Creates a session with device credentials.
     *
     * @param credentialType Device credential type
     * @param credentialMap  Device credential map
     * @return session id
     * @throws AuthenticationException if device credentials were not able to be validated
     */
    public String createSession(String credentialType, Map<String, String> credentialMap)
            throws AuthenticationException {
        try {
            Session session = SessionCreator.createSession(credentialType, credentialMap);
            String createdSession = addSessionInternal(session);
<<<<<<< HEAD
            logger.atDebug().log("Successfully created a session with device credentials");
=======
>>>>>>> 8e3a266 (feat: add metrics for the Get Client Device Auth Token API (#207))
            domainEvents.emit(new SessionCreationEvent(SessionCreationEvent
                    .SessionCreationStatus.SUCCESS));
            return createdSession;
        } catch (AuthenticationException e) {
            domainEvents.emit(new SessionCreationEvent(SessionCreationEvent
                    .SessionCreationStatus.FAILURE));
            throw e;
        }
    }

    /**
     * Closes a session.
     *
     * @param sessionId session identifier
     */
    public void closeSession(String sessionId) {
        logger.atDebug().kv(SESSION_ID, sessionId).log("Closing session");
        closeSessionInternal(sessionId);
    }

    /**
     * Session configuration setter.
     *
     * @param sessionConfig session configuration
     */
    public void setSessionConfig(SessionConfig sessionConfig) {
        this.sessionConfig = sessionConfig;
    }

    private synchronized void closeSessionInternal(String sessionId) {
        sessionMap.remove(sessionId);
    }

    // Returns a session ID which can be returned to the client
    private synchronized String addSessionInternal(Session session) {
        String sessionId = generateSessionId();
        logger.atDebug().kv(SESSION_ID, sessionId).log("Creating new session");
        sessionMap.put(sessionId, session);
        return sessionId;
    }

    private String generateSessionId() {
        String sessionId;
        do {
            sessionId = UUID.randomUUID().toString();
        } while (sessionMap.containsKey(sessionId));
        return sessionId;
    }

    private int getSessionCapacity() {
        if (sessionConfig == null) {
            return SessionConfig.DEFAULT_SESSION_CAPACITY;
        }
        return sessionConfig.getSessionCapacity();
    }
}
