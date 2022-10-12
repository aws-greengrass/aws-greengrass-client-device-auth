/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;


import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.SECURITY_TOPIC;
import static com.aws.greengrass.clientdevices.auth.session.SessionConfig.MAX_CLIENT_DEVICE_TRUST_DURATION_HOURS;
import static com.aws.greengrass.clientdevices.auth.session.SessionConfig.MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.DEFAULT_MAX_ACTIVE_AUTH_TOKENS;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.MAX_ACTIVE_AUTH_TOKENS_TOPIC;
import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.PERFORMANCE_TOPIC;
import static com.aws.greengrass.clientdevices.auth.session.SessionConfig.MAX_SESSION_CAPACITY;
import static com.aws.greengrass.clientdevices.auth.session.SessionConfig.MIN_SESSION_CAPACITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@ExtendWith(GGExtension.class)
class SessionConfigTest {
    private Topics configurationTopics;
    private SessionConfig sessionConfig;

    @BeforeEach
    void beforeEach() {
        configurationTopics = Topics.of(new Context(), CONFIGURATION_CONFIG_KEY, null);
        sessionConfig = SessionConfig.from(configurationTopics);
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    public void GIVEN_noConfiguredCapacity_WHEN_getSessionCapacity_THEN_returnsDefaultCapacity() {
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(DEFAULT_MAX_ACTIVE_AUTH_TOKENS)));
    }

    @Test
    public void GIVEN_defaultSessionCapacity_WHEN_updateConfiguration_THEN_returnsUpdatedCapacity() {
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(DEFAULT_MAX_ACTIVE_AUTH_TOKENS)));
        int newCapacity = 1;
        configurationTopics.lookup(PERFORMANCE_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC)
                .withValue(newCapacity);
        // block until config changes are merged in
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(newCapacity)));
    }

    @Test
    public void GIVEN_invalidConfigured_sessionCapacity_WHEN_getSessionCapacity_THEN_returnsClampedCapacity() {
        // zero capacity
        int configuredCapacity = 0;
        configurationTopics.lookup(PERFORMANCE_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC).withValue(configuredCapacity);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(MIN_SESSION_CAPACITY)));

        // overflown integer
        int overflown = Integer.MAX_VALUE + 1;
        configurationTopics.lookup(PERFORMANCE_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC).withValue(overflown);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(MIN_SESSION_CAPACITY)));

        // integer max value
        configurationTopics.lookup(PERFORMANCE_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC).withValue(Integer.MAX_VALUE);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(MAX_SESSION_CAPACITY)));

        String empty = "";
        configurationTopics.lookup(PERFORMANCE_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC).withValue(empty);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(MIN_SESSION_CAPACITY)));
    }

    @Test
    public void GIVEN_noConfiguredTrustDuration_WHEN_getTrustDuration_THEN_returnsDefaultDurationDays() {
        assertThat(sessionConfig.getClientDeviceTrustDurationDays(), is(equalTo(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS)));
    }

    @Test
    public void GIVEN_defaultTrustDuration_WHEN_updateTrustDurationDays_THEN_returnsUpdatedTrustDurationDays() {
        assertThat(sessionConfig.getClientDeviceTrustDurationDays(), is(equalTo(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS)));
        int newTrustDuration = 100;
        configurationTopics.lookup(SECURITY_TOPIC, CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC)
                .withValue(newTrustDuration);
        // block until config changes are merged in
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getClientDeviceTrustDurationDays(), is(equalTo(newTrustDuration)));
    }

    @Test
    public void GIVEN_invalidConfiguredTrustDurationDays_WHEN_getTrustDuration_THEN_returnsClampedTrustDurationDays() {
        // zero trust duration
        int configuredTrustDuration = 0;
        configurationTopics.lookup(SECURITY_TOPIC, CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC).withValue(configuredTrustDuration);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getClientDeviceTrustDurationDays(), is(equalTo(MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS)));

        // overflown integer
        int overflown = Integer.MAX_VALUE + 1;
        configurationTopics.lookup(SECURITY_TOPIC, CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC).withValue(overflown);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getClientDeviceTrustDurationDays(), is(equalTo(MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS)));

        // integer max value
        configurationTopics.lookup(SECURITY_TOPIC, CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC).withValue(Integer.MAX_VALUE);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getClientDeviceTrustDurationDays(), is(equalTo(MAX_CLIENT_DEVICE_TRUST_DURATION_HOURS)));

        String empty = "";
        configurationTopics.lookup(SECURITY_TOPIC, CLIENT_DEVICE_TRUST_DURATION_HOURS_TOPIC).withValue(empty);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getClientDeviceTrustDurationDays(), is(equalTo(MIN_CLIENT_DEVICE_TRUST_DURATION_HOURS)));
    }
}
