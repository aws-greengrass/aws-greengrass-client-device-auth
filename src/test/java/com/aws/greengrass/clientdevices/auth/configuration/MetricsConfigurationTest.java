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
import java.util.Optional;

import static com.aws.greengrass.clientdevices.auth.configuration.MetricsConfiguration.EMITTING_FREQUENCY;
import static com.aws.greengrass.clientdevices.auth.configuration.MetricsConfiguration.ENABLE_METRICS;
import static com.aws.greengrass.clientdevices.auth.configuration.MetricsConfiguration.METRICS_TOPIC;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class MetricsConfigurationTest {
    private Topics configurationTopics;

    @BeforeEach
    void beforeEach() {
        configurationTopics = Topics.of(new Context(), CONFIGURATION_CONFIG_KEY, null);
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    public void GIVEN_cdaDefaultConfiguration_WHEN_getEnableMetrics_THEN_returnsNull() {
        MetricsConfiguration metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertFalse(metricsConfiguration.getEnableMetrics().isPresent());
    }

    @Test
    public void GIVEN_cdaDefaultConfiguration_WHEN_getEmittingFrequency_THEN_returnsNull() {
        MetricsConfiguration metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertFalse(metricsConfiguration.getEmittingFrequency().isPresent());
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_metricsEnabled_THEN_getEnableMetricsReturnsTrue() {
        MetricsConfiguration metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertFalse(metricsConfiguration.getEnableMetrics().isPresent());
        configurationTopics.lookup(METRICS_TOPIC, ENABLE_METRICS).withValue(true);
        metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertEquals(true, metricsConfiguration.getEnableMetrics().get());
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_metricsEnabledAndFrequencyProvided_THEN_getEmittingFrequencyReturnsInt() {
        MetricsConfiguration metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertFalse(metricsConfiguration.getEmittingFrequency().isPresent());
        configurationTopics.lookup(METRICS_TOPIC, EMITTING_FREQUENCY).withValue(10_000);
        metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertEquals(10_000, metricsConfiguration.getEmittingFrequency().get());
    }
}
