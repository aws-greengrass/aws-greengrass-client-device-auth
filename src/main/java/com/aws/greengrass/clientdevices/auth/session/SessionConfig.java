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

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.DEFAULT_MAX_ACTIVE_AUTH_TOKENS;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.MAX_ACTIVE_AUTH_TOKENS_TOPIC;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.PERFORMANCE_TOPIC;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.SECURITY_TOPIC;

@SuppressWarnings("PMD.DataClass")
public final class SessionConfig {
    private static final Logger LOGGER = LogManager.getLogger(SessionConfig.class);
    public static final int DEFAULT_SESSION_CAPACITY = DEFAULT_MAX_ACTIVE_AUTH_TOKENS;
    // valid session capacity should be within range [1, Integer.MAX_VALUE)
    // to be able to initialize and perform appropriate eviction check in LRU session cache
    public static final int MIN_SESSION_CAPACITY = 1;
    public static final int MAX_SESSION_CAPACITY = Integer.MAX_VALUE - 1;
    public static final int MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS = 0;
    public static final int MAX_CLIENT_DEVICE_TRUST_DURATION_HOURS = Integer.MAX_VALUE;

    private final AtomicInteger sessionCapacity = new AtomicInteger(DEFAULT_SESSION_CAPACITY);
    private final AtomicInteger clientDeviceTrustDuration =
            new AtomicInteger(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS);

    private final Topics configuration;

    /**
     * Constructor.
     *
     * @param configuration Configuration topic for this service
     */
    private SessionConfig(Topics configuration) {
        this.configuration = configuration;
        this.sessionCapacity.set(getConfiguredSessionCapacity());

        this.configuration.subscribe((whatHappened, node) -> {
            // update session capacity to the latest configured value
            updateSessionCapacity(getConfiguredSessionCapacity());
            updateClientDeviceTrustDurationDays(getConfiguredTrustDuration());
        });
    }

    public static SessionConfig from(Topics configuration) {
        return new SessionConfig(configuration);
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
     * Get configured client device trust duration.
     *
     * @return client device trust duration in hours
     */
    public int getClientDeviceTrustDurationHours() {
        return clientDeviceTrustDuration.get();
    }

    private void updateClientDeviceTrustDurationDays(int newTrustDuration) {
        clientDeviceTrustDuration.set(newTrustDuration);
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

        return getClampedConfigValue(MAX_ACTIVE_AUTH_TOKENS_TOPIC, configValue,
                MIN_SESSION_CAPACITY, MAX_SESSION_CAPACITY);
    }

    /**
     * Retrieves configured client device trust duration.
     * Invalid values are clamped to the valid range
     *
     * @return session capacity value
     */
    private int getConfiguredTrustDuration() {
        if (configuration == null || configuration.isEmpty()) {
            return DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS;
        }

        int configValue = Coerce.toInt(configuration.findOrDefault(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS,
                SECURITY_TOPIC, CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC));

        return getClampedConfigValue(CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC, configValue,
                MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS, MAX_CLIENT_DEVICE_TRUST_DURATION_HOURS);
    }
    
    private int getClampedConfigValue(String configKey, int configValue, int min, int max) {
        int clamped = Math.max(min, Math.min(max, configValue));
        if (clamped != configValue) {
            LOGGER.warn("Illegal value {} for configuration {}. Using clamped value {}",
                    configValue, configKey, clamped);
            return clamped;
        }
        return configValue;
    }
}
