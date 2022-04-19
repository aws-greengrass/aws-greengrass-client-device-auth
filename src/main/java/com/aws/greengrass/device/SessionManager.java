/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
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
    public static final String SESSION_ID = "sessionId";

    @Getter(AccessLevel.PACKAGE)
    private final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * find session by id.
     *
     * @param sessionId session identifier
     * @return session or null
     */
    public Session findSession(String sessionId) {
        return sessionMap.getOrDefault(sessionId, null);
    }

    /**
     * create session with certificate.
     *
     * @param certificate certificate stored in session
     * @return session id
     */
    public String createSession(Certificate certificate) {
        String sessionId = generateSessionId();
        Session session = sessionMap.putIfAbsent(sessionId, new SessionImpl(certificate));
        if (session != null) {
            return createSession(certificate);
        }
        logger.atInfo().kv(SESSION_ID, sessionId).log("Created the session");
        return sessionId;
    }

    String generateSessionId() {
        //TODO better session id format?
        return UUID.randomUUID().toString();
    }

    /**
     * close the session by id.
     *
     * @param sessionId session identifier
     */
    public void closeSession(String sessionId) {
        logger.atInfo().kv(SESSION_ID, sessionId).log("Closing the session");
        Session session = sessionMap.get(sessionId);
        sessionMap.remove(sessionId, session);
    }
}
