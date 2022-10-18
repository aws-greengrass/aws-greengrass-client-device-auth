/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;
import lombok.Getter;

import java.util.Objects;

/**
 * Represents the security configuration. Acts as an adapter from the GG Topics to the domain.
 * <p>
 * |---- configuration
 * |    |---- security:
 * |          |---- clientDeviceTrustDurationMinutes: "..."
 * </p>
 */
@Getter
public final class SecurityConfiguration {
    private static final Logger logger = LogManager.getLogger(SecurityConfiguration.class);
    public static final String SECURITY_TOPIC = "security";
    public static final String CLIENT_DEVICE_TRUST_DURATION_MINUTES_TOPIC = "clientDeviceTrustDurationMinutes";
    // opt-in trust verification: metadata trust verification is enabled when configured trust duration is non-zero
    // trust verification is disabled by default
    public static final int DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES = 0;
    public static final int MIN_CLIENT_DEVICE_TRUST_DURATION_MINUTES = 0;

    private int clientDeviceTrustDurationMinutes;


    private SecurityConfiguration(int clientDeviceTrustDurationMinutes) {
        this.clientDeviceTrustDurationMinutes = clientDeviceTrustDurationMinutes;
    }

    /**
     * Factory method for creating an immutable SecurityConfiguration.
     *
     * @param configurationTopics service configuration
     */
    public static SecurityConfiguration from(Topics configurationTopics) {
        Topics securityTopics = configurationTopics.lookupTopics(SECURITY_TOPIC);

        return new SecurityConfiguration(
                getClientDeviceTrustDurationMinutes(securityTopics)
        );
    }

    /**
     * Compares new configuration with itself.
     *
     * @return boolean indicating whether config has changed
     */
    boolean hasChanged(SecurityConfiguration newConfig) {
        if (newConfig == null) {
            return true;
        }

        return !Objects.equals(newConfig.getClientDeviceTrustDurationMinutes(),
                getClientDeviceTrustDurationMinutes());
    }

    private static int getClientDeviceTrustDurationMinutes(Topics securityTopics) {
        int configValue = Coerce.toInt(securityTopics.findOrDefault(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES,
                CLIENT_DEVICE_TRUST_DURATION_MINUTES_TOPIC));
        // overflown integer
        if (configValue < 0) {
            logger.warn("Illegal value {} for configuration {}. Using minimum value {}",
                    configValue, CLIENT_DEVICE_TRUST_DURATION_MINUTES_TOPIC, MIN_CLIENT_DEVICE_TRUST_DURATION_MINUTES);
            configValue = MIN_CLIENT_DEVICE_TRUST_DURATION_MINUTES;
        }
        return configValue;
    }
}
