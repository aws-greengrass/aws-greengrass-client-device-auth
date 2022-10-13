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

/**
 * Represents the security configuration. Acts as an adapter from the GG Topics to the domain.
 * <p>
 * |---- configuration
 * |    |---- security:
 * |          |---- clientDeviceTrustDurationHours: "..."
 * </p>
 */
@Getter
public final class SecurityConfiguration {
    private static final Logger logger = LogManager.getLogger(SecurityConfiguration.class);
    public static final String SECURITY_TOPIC = "security";
    public static final String CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC = "clientDeviceTrustDurationHours";
    public static final int DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS = 24;
    public static final int MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS = 0;
    public static final int MAX_CLIENT_DEVICE_TRUST_DURATION_HOURS = Integer.MAX_VALUE;

    private int clientDeviceTrustDurationHours;


    private SecurityConfiguration(int clientDeviceTrustDurationHours) {
        this.clientDeviceTrustDurationHours = clientDeviceTrustDurationHours;
    }

    /**
     * Factory method for creating an immutable SecurityConfiguration.
     *
     * @param configurationTopics service configuration
     */
    public static SecurityConfiguration from(Topics configurationTopics) {
        Topics securityTopics = configurationTopics.lookupTopics(SECURITY_TOPIC);

        return new SecurityConfiguration(
                getClientDeviceTrustDurationHours(securityTopics)
        );
    }

    private static int getClientDeviceTrustDurationHours(Topics securityTopics) {
        int configValue = Coerce.toInt(securityTopics.findOrDefault(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS,
                CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC));
        return clampIfInvalid(CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC, configValue,
                MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS, MAX_CLIENT_DEVICE_TRUST_DURATION_HOURS);
    }

    private static int clampIfInvalid(String configKey, int configValue, int min, int max) {
        int clamped = Math.max(min, Math.min(max, configValue));
        if (clamped != configValue) {
            logger.warn("Illegal value {} for configuration {}. Using clamped value {}",
                    configValue, configKey, clamped);
            return clamped;
        }
        return configValue;
    }

}
