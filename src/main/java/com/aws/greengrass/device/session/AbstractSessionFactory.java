/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;

import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AbstractSessionFactory {
    private static final Logger logger = LogManager.getLogger(AbstractSessionFactory.class);

    @SuppressWarnings("PMD.UnusedPrivateField")
    private final Map<String, SessionFactory> factoryMap;

    private AbstractSessionFactory() {
        factoryMap = new ConcurrentHashMap<>();
    }

    private static class SessionFactorySingleton {
        @SuppressWarnings("PMD.AccessorClassGeneration")
        private static final AbstractSessionFactory INSTANCE = new AbstractSessionFactory();
    }

    /**
     * Create a client device session.
     *
     * @param credentialType type of credentials provided
     * @param credentialMap  map of client credentials
     * @return new session if the client can be authenticated
     * @throws AuthenticationException if the client fails to be authenticated
     */
    public static Session createSession(String credentialType, Map<String, String> credentialMap)
            throws AuthenticationException {
        SessionFactory sessionFactory = SessionFactorySingleton.INSTANCE.factoryMap.get(credentialType);
        if (sessionFactory == null) {
            logger.atWarn().kv("credentialType", credentialType)
                .log("no registered handler to process device credentials");
            throw new IllegalArgumentException("unknown credential type");
        }
        return sessionFactory.createSession(credentialMap);
    }

    public static void registerSessionFactory(String credentialType, SessionFactory sessionFactory) {
        SessionFactorySingleton.INSTANCE.factoryMap.put(credentialType, sessionFactory);
    }

    public static void unregisterSessionFactory(String credentialType) {
        SessionFactorySingleton.INSTANCE.factoryMap.remove(credentialType);
    }
}
