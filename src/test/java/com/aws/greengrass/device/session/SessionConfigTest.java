/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;


import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.device.ClientDevicesAuthService.DEFAULT_MAX_ACTIVE_AUTH_TOKENS;
import static com.aws.greengrass.device.ClientDevicesAuthService.DEVICE_AUTH_TOKEN_TOPIC;
import static com.aws.greengrass.device.ClientDevicesAuthService.MAX_ACTIVE_AUTH_TOKENS_TOPIC;
import static com.aws.greengrass.device.ClientDevicesAuthService.SETTINGS_TOPIC;
import static com.aws.greengrass.device.session.SessionConfig.MAX_SESSION_CAPACITY;
import static com.aws.greengrass.device.session.SessionConfig.MIN_SESSION_CAPACITY;
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
        sessionConfig = new SessionConfig(configurationTopics);
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    public void GIVEN_no_configured_capacity_WHEN_getSessionCapacity_THEN_returns_default_capacity() {
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(DEFAULT_MAX_ACTIVE_AUTH_TOKENS)));
    }

    @Test
    public void GIVEN_default_session_capacity_WHEN_update_configuration_THEN_returns_updated_capacity() {
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(DEFAULT_MAX_ACTIVE_AUTH_TOKENS)));
        int newCapacity = 1;
        configurationTopics.lookup(SETTINGS_TOPIC, DEVICE_AUTH_TOKEN_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC)
                .withValue(newCapacity);
        // block until config changes are merged in
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(newCapacity)));
    }

    @Test
    public void GIVEN_invalid_configured_session_capacity_WHEN_getSessionCapacity_THEN_returns_clamped_capacity() {
        // zero capacity
        int configuredCapacity = 0;
        configurationTopics.lookup(SETTINGS_TOPIC, DEVICE_AUTH_TOKEN_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC)
                .withValue(configuredCapacity);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(MIN_SESSION_CAPACITY)));

        // overflown integer
        int overflown = Integer.MAX_VALUE + 1;
        configurationTopics.lookup(SETTINGS_TOPIC, DEVICE_AUTH_TOKEN_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC)
                .withValue(overflown);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(MIN_SESSION_CAPACITY)));

        // integer max value
        configurationTopics.lookup(SETTINGS_TOPIC, DEVICE_AUTH_TOKEN_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC)
                .withValue(Integer.MAX_VALUE);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(MAX_SESSION_CAPACITY)));

        String empty = "";
        configurationTopics.lookup(SETTINGS_TOPIC, DEVICE_AUTH_TOKEN_TOPIC, MAX_ACTIVE_AUTH_TOKENS_TOPIC)
                .withValue(empty);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(MIN_SESSION_CAPACITY)));
    }
}
