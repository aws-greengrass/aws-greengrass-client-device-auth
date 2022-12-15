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

import static com.aws.greengrass.clientdevices.auth.configuration.MetricsConfiguration.DEFAULT_PERIODIC_AGGREGATE_INTERVAL_SEC;
import static com.aws.greengrass.clientdevices.auth.configuration.MetricsConfiguration.AGGREGATE_PERIOD;
import static com.aws.greengrass.clientdevices.auth.configuration.MetricsConfiguration.DISABLE_METRICS;
import static com.aws.greengrass.clientdevices.auth.configuration.MetricsConfiguration.METRICS_TOPIC;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void GIVEN_cdaDefaultConfiguration_WHEN_getDisableMetrics_THEN_returnsFalse() {
        MetricsConfiguration metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertFalse(metricsConfiguration.isDisableMetrics());
    }

    @Test
    public void GIVEN_cdaDefaultConfiguration_WHEN_getAggregatePeriod_THEN_returnsDefaultPeriodicAggregateInterval() {
        MetricsConfiguration metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertEquals(DEFAULT_PERIODIC_AGGREGATE_INTERVAL_SEC, metricsConfiguration.getAggregatePeriod());
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_metricsDisabled_THEN_getDisableMetricsReturnsTrue() {
        MetricsConfiguration metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertEquals(DEFAULT_PERIODIC_AGGREGATE_INTERVAL_SEC, metricsConfiguration.getAggregatePeriod());
        configurationTopics.lookup(METRICS_TOPIC, DISABLE_METRICS).withValue(true);
        metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertTrue(metricsConfiguration.isDisableMetrics());
    }

    @Test
    public void GIVEN_cdaConfiguration_WHEN_metricsEnabledAndFrequencyProvided_THEN_getAggregatePeriodReturnsInt() {
        MetricsConfiguration metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertEquals(DEFAULT_PERIODIC_AGGREGATE_INTERVAL_SEC, metricsConfiguration.getAggregatePeriod());
        configurationTopics.lookup(METRICS_TOPIC, AGGREGATE_PERIOD).withValue(10_000);
        metricsConfiguration = MetricsConfiguration.from(configurationTopics);
        assertEquals(10_000, metricsConfiguration.getAggregatePeriod());
    }
}
