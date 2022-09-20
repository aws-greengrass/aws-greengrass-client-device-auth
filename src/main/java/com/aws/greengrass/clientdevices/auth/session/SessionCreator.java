/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionCreator {
    private static final Logger logger = LogManager.getLogger(SessionCreator.class);

    private final Map<String, SessionFactory> factoryMap;

    public SessionCreator() {
        factoryMap = new ConcurrentHashMap<>();
    }

    /**
     * Create a client device session.
     *
     * @param credentialType type of credentials provided
     * @param credentialMap  map of client credentials
     * @return new session if the client can be authenticated
     * @throws AuthenticationException if the client fails to be authenticated
     */
    public Session createSession(String credentialType, Map<String, String> credentialMap)
            throws AuthenticationException {
        SessionFactory sessionFactory = factoryMap.get(credentialType);
        if (sessionFactory == null) {
            logger.atWarn().kv("credentialType", credentialType)
                .log("no registered handler to process device credentials");
            throw new IllegalArgumentException("unknown credential type");
        }
        return sessionFactory.createSession(credentialMap);
    }

    public void registerSessionFactory(String credentialType, SessionFactory sessionFactory) {
        factoryMap.put(credentialType, sessionFactory);
    }
}
