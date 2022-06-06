/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;


import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

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
    public void GIVEN_default_configuration_WHEN_getSessionCapacity_THEN_returns_default_capacity() {
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(SessionConfig.DEFAULT_SESSION_CAPACITY)));
    }

    @Test
    public void GIVEN_default_session_capacity_WHEN_update_configuration_THEN_returns_updated_capacity() {
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(SessionConfig.DEFAULT_SESSION_CAPACITY)));
        int newCapacity = 1;
        configurationTopics.lookup(SessionConfig.CLIENT_DEVICE_AUTH_SESSION_CAPACITY_TOPIC).withValue(newCapacity);
        // block until config changes are merged in
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(newCapacity)));
    }

    @Test
    public void GIVEN_invalid_configured_session_capacity_WHEN_getSessionCapacity_THEN_returns_default_capacity() {
        // capacity beyond maximum possible integer
        long configuredCapacity = Integer.MAX_VALUE + 1L;
        configurationTopics.lookup(SessionConfig.CLIENT_DEVICE_AUTH_SESSION_CAPACITY_TOPIC)
                .withValue(configuredCapacity);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(SessionConfig.DEFAULT_SESSION_CAPACITY)));

        // zero capacity
        configuredCapacity = 0L;
        configurationTopics.lookup(SessionConfig.CLIENT_DEVICE_AUTH_SESSION_CAPACITY_TOPIC)
                .withValue(configuredCapacity);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(SessionConfig.DEFAULT_SESSION_CAPACITY)));

        // overflown integer
        int overflown = Integer.MAX_VALUE + 1;
        configurationTopics.lookup(SessionConfig.CLIENT_DEVICE_AUTH_SESSION_CAPACITY_TOPIC)
                .withValue(overflown);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(SessionConfig.DEFAULT_SESSION_CAPACITY)));

        String empty = "";
        configurationTopics.lookup(SessionConfig.CLIENT_DEVICE_AUTH_SESSION_CAPACITY_TOPIC).withValue(empty);
        configurationTopics.context.waitForPublishQueueToClear();
        assertThat(sessionConfig.getSessionCapacity(), is(equalTo(SessionConfig.DEFAULT_SESSION_CAPACITY)));
    }
}