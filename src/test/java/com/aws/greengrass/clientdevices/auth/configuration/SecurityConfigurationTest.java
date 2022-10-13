/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;


import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC;
import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS;
import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.MAX_CLIENT_DEVICE_TRUST_DURATION_HOURS;
import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS;
import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.SECURITY_TOPIC;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class SecurityConfigurationTest {
    private Topics configurationTopics;
    private SecurityConfiguration securityConfig;

    @BeforeEach
    void beforeEach() {
        configurationTopics = Topics.of(new Context(), CONFIGURATION_CONFIG_KEY, null);
        securityConfig = SecurityConfiguration.from(configurationTopics);
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    public void GIVEN_noConfiguredTrustDuration_WHEN_getClientDeviceTrustDurationHours_THEN_returnDefaultTrustDuration() {
        assertThat(securityConfig.getClientDeviceTrustDurationHours(), 
                is(equalTo(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS)));
    }

    @Test
    public void GIVEN_invalid_configured_session_capacity_WHEN_getSessionCapacity_THEN_returns_clamped_capacity() {
        // zero capacity
        int configuredTrustDuration = 0;
        configurationTopics.lookup(SECURITY_TOPIC, CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC).withValue(configuredTrustDuration);
        securityConfig = SecurityConfiguration.from(configurationTopics);
        assertThat(securityConfig.getClientDeviceTrustDurationHours(), is(equalTo(MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS)));

        // overflown integer
        int overflown = Integer.MAX_VALUE + 1;
        configurationTopics.lookup(SECURITY_TOPIC, CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC).withValue(overflown);
        securityConfig = SecurityConfiguration.from(configurationTopics);
        assertThat(securityConfig.getClientDeviceTrustDurationHours(), is(equalTo(MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS)));

        // integer max value
        configurationTopics.lookup(SECURITY_TOPIC, CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC).withValue(Integer.MAX_VALUE);
        securityConfig = SecurityConfiguration.from(configurationTopics);
        assertThat(securityConfig.getClientDeviceTrustDurationHours(), is(equalTo(MAX_CLIENT_DEVICE_TRUST_DURATION_HOURS)));

        String empty = "";
        configurationTopics.lookup(SECURITY_TOPIC, CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC).withValue(empty);
        securityConfig = SecurityConfiguration.from(configurationTopics);
        assertThat(securityConfig.getClientDeviceTrustDurationHours(), is(equalTo(MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS)));
    }

}
