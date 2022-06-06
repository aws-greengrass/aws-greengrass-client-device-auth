/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;

import java.util.concurrent.atomic.AtomicInteger;

public class SessionConfig {
    private static final Logger LOGGER = LogManager.getLogger(SessionConfig.class);
    // arbitrarily set default
    protected static final int DEFAULT_SESSION_CAPACITY = 1000;
    public static final String SESSION_CAPACITY_TOPIC = "sessionCapacity";

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

    private int getConfiguredSessionCapacity() {
        // valid session capacity should be within range [1, Integer.MAX_VALUE)
        return getIntConfigValueRangeCheck(SESSION_CAPACITY_TOPIC, DEFAULT_SESSION_CAPACITY,
                1, Integer.MAX_VALUE - 1);
    }

    /**
     * Helper method that handles integer configuration parameters with a range check.
     *
     * @param configParameter   name of the configuration parameter
     * @param defaultValue  default value
     * @param low   low end of the range check
     * @param high  high end of the range check
     * @return returns the parsed value of the parameter.
     *      If the value passed to the parameter is not a valid integer,
     *      or within the specified range, the default value for the parameter will be returned.
     */
    private int getIntConfigValueRangeCheck(String configParameter, int defaultValue, int low, int high) {
        if (configuration == null || configuration.isEmpty()) {
            return defaultValue;
        }
        Object configValue = configuration.findOrDefault(defaultValue, configParameter);
        int intValue = Coerce.toInt(configValue);
        if (intValue < low || intValue > high) {
            LOGGER.warn("Illegal value {} for configuration {}. Using default value {}",
                    configValue, configParameter, defaultValue);
            return defaultValue;
        }
        return intValue;
    }
}
