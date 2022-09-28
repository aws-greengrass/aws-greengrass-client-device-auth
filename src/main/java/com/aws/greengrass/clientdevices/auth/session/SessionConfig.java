/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;

import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("PMD.DataClass")
public class SessionConfig {
    private static final Logger LOGGER = LogManager.getLogger(SessionConfig.class);
    public static final int DEFAULT_SESSION_CAPACITY = 2500;
    // valid session capacity should be within range [1, Integer.MAX_VALUE)
    // to be able to initialize and perform appropriate eviction check in LRU session cache
    public static final int MIN_SESSION_CAPACITY = 1;
    public static final int MAX_SESSION_CAPACITY = Integer.MAX_VALUE - 1;

    public static final String PERFORMANCE_TOPIC = "performance";
    public static final String MAX_ACTIVE_AUTH_TOKENS_TOPIC = "maxActiveAuthTokens";

    private final AtomicInteger sessionCapacity = new AtomicInteger(DEFAULT_SESSION_CAPACITY);

    private final Topics configuration;

    /**
     * Constructor.
     *
     * @param configuration Configuration topic for this service
     */
    public SessionConfig(Topics configuration) {
        this.configuration = configuration;
        this.sessionCapacity.set(getConfiguredSessionCapacity());

        this.configuration.subscribe((whatHappened, node) -> {
            // update session capacity to the latest configured value
            updateSessionCapacity(getConfiguredSessionCapacity());
        });
    }

    /**
     * Get configured Client-Device-Auth Session capacity.
     *
     * @return session capacity
     */
    public int getSessionCapacity() {
        return sessionCapacity.get();
    }

    /**
     * Updates Client-Device-Auth Session capacity to the desired int value.
     *
     * @param newCapacity desired Client-Device-Auth Session capacity
     */
    private void updateSessionCapacity(int newCapacity) {
        sessionCapacity.set(newCapacity);
    }

    /**
     * Retrieves configured Session Capacity.
     * Invalid values are clamped to the valid range.
     *
     * @return session capacity value
     */
    private int getConfiguredSessionCapacity() {
        if (configuration == null || configuration.isEmpty()) {
            return DEFAULT_SESSION_CAPACITY;
        }
        int configValue = Coerce.toInt(configuration.findOrDefault(DEFAULT_SESSION_CAPACITY,
                PERFORMANCE_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC));

        int clamped = Math.max(MIN_SESSION_CAPACITY, Math.min(MAX_SESSION_CAPACITY, configValue));
        if (clamped != configValue) {
            LOGGER.warn("Illegal value {} for configuration {}. Using clamped value {}",
                    configValue, MAX_ACTIVE_AUTH_TOKENS_TOPIC, clamped);
            return clamped;
        }
        return configValue;
    }
}
