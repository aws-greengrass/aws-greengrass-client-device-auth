/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;
import lombok.Getter;

/**
 * Represents the metrics part of the component configuration. Acts as an adapter from the GG topics to the domain.
 * <p>
 * |---- configuration
 * |    |---- metrics:
 * |          |---- disableMetrics
 * |          |---- aggregatePeriod
 * </p>
 */

public final class MetricsConfiguration {
    public static final String METRICS_TOPIC = "metrics";
    public static final String DISABLE_METRICS = "disableMetrics";
    public static final String AGGREGATE_PERIOD = "aggregatePeriod";
    public static final int DEFAULT_PERIODIC_AGGREGATE_INTERVAL_SEC = 3_600;
    @Getter
    private boolean disableMetrics;
    @Getter
    private int aggregatePeriod;

    private MetricsConfiguration(boolean disableMetrics, int aggregatePeriod) {
        this.disableMetrics = disableMetrics;
        this.aggregatePeriod = aggregatePeriod;
    }

    /**
     * Create a MetricsConfiguration from the service configuration.
     *
     * @param configurationTopics the configuration key of the service configuration
     * @return MetricsConfiguration
     */
    public static MetricsConfiguration from(Topics configurationTopics) {
        Topics metricsTopic = configurationTopics.lookupTopics(METRICS_TOPIC);

        return new MetricsConfiguration(getDisableMetricsFlagFromConfiguration(metricsTopic),
                getAggregatePeriodFromConfiguration(metricsTopic));
    }

    /**
     * Compares 2 Metric Configurations and returns true if there's been a change.
     *
     * @param config Metric Configuration
     * @return true if changed, else false
     */
    public boolean hasChanged(MetricsConfiguration config) {
        return config.disableMetrics != disableMetrics
                || config.getAggregatePeriod() != getAggregatePeriod();
    }

    private static boolean getDisableMetricsFlagFromConfiguration(Topics metricsTopic) {
        return Coerce.toBoolean(metricsTopic.find(DISABLE_METRICS));
    }

    private static int getAggregatePeriodFromConfiguration(Topics metricsTopic) {
        int aggregatePeriod = Coerce.toInt(metricsTopic.find(AGGREGATE_PERIOD));

        if (aggregatePeriod != 0) {
            return aggregatePeriod;
        } else {
            return DEFAULT_PERIODIC_AGGREGATE_INTERVAL_SEC;
        }
    }
}
