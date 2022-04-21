/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;

import com.aws.greengrass.device.exception.AuthenticationException;
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
    public static final String SESSION_ID = "SessionId";

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
        return sessionMap.getOrDefault(internalSessionId, null).getRight();
    }

    /**
     * create session with certificate.
     *
     * @param credentialType Device credential type
     * @param credentialMap  Device credential map
     * @return session id
     * @throws AuthenticationException if device credentials were not able to be validated
     */
    public String createSession(String credentialType, Map<String, String> credentialMap)
            throws AuthenticationException {
        Session session = AbstractSessionFactory.createSession(credentialType, credentialMap);
        String externalId = generateSessionId();
        while (externalToInternalSessionMap.containsKey(externalId)) {
            externalId = generateSessionId();
        }
        String internalId = getInternalSessionId(credentialMap);
        logger.atDebug().kv(SESSION_ID, externalId).log("Creating new session");
        String previousExternalId = addSessionInternal(externalId, internalId, session);
        if (previousExternalId != null) {
            logger.atDebug().kv(SESSION_ID, previousExternalId).log("Evicting previous session");
        }
        return externalId;
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

    String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    private String getInternalSessionId(Map<String, String> credentialMap) {
        return String.valueOf(credentialMap.hashCode());
    }

    private synchronized void closeSessionInternal(String externalId) {
        String internalSessionId = externalToInternalSessionMap.get(externalId);
        if (internalSessionId == null) {
            // Session has already been evicted
            return;
        }
        externalToInternalSessionMap.remove(externalId, internalSessionId);
        Session session = sessionMap.get(internalSessionId).getRight();
        sessionMap.remove(internalSessionId, session);
    }

    /*
     * Returns an external session ID, if one was present and was evicted. Else, null
     */
    private synchronized String addSessionInternal(String externalId, String internalId, Session session) {
        Pair<String, Session> existingPair = sessionMap.get(internalId);
        sessionMap.put(internalId, new Pair<>(externalId, session));
        externalToInternalSessionMap.put(externalId, internalId);
        if (existingPair != null) {
            externalToInternalSessionMap.remove(existingPair.getLeft());
            return existingPair.getLeft();
        }
        return null;
    }
}
