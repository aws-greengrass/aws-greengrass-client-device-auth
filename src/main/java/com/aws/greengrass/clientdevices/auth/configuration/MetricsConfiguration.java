/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;
import lombok.Getter;

import java.util.Optional;

/**
 * Represents the metrics part of the component configuration. Acts as an adapter from the GG topics to the domain.
 * <p>
 * |---- configuration
 * |    |---- metrics:
 * |          |---- emitMetrics
 * |          |---- emittingFrequency
 * </p>
 */

public final class MetricsConfiguration {
    public static final String METRICS_TOPIC = "metrics";
    public static final String ENABLE_METRICS = "enableMetrics";
    public static final String EMITTING_FREQUENCY = "emittingFrequency";
    @Getter
    private Optional<Boolean> enableMetrics;
    @Getter
    private Optional<Integer> emittingFrequency;

    private MetricsConfiguration(Optional<Boolean> enableMetrics, Optional<Integer> emittingFrequency) {
        this.enableMetrics = enableMetrics;
        this.emittingFrequency = emittingFrequency;
    }

    /**
     * Create a MetricsConfiguration from the service configuration.
     *
     * @param configurationTopics the configuration key of the service configuration
     * @return MetricsConfiguration
     */
    public static MetricsConfiguration from(Topics configurationTopics) {
        Topics metricsTopic = configurationTopics.lookupTopics(METRICS_TOPIC);

        return new MetricsConfiguration(getEnableMetricsFlagFromConfiguration(metricsTopic),
                getEmittingFrequencyFromConfiguration(metricsTopic));
    }

    /**
     * Compares 2 Metric Configurations and returns true if there's been a change.
     *
     * @param config Metric Configuration
     * @return true if changed, else false
     */
    public boolean hasChanged(MetricsConfiguration config) {
        return config.getEnableMetrics() != getEnableMetrics() ||
                config.getEmittingFrequency() != getEmittingFrequency();
    }

    private static Optional<Boolean> getEnableMetricsFlagFromConfiguration(Topics metricsTopic) {
        boolean emit = Coerce.toBoolean(metricsTopic.find(ENABLE_METRICS));

        if (emit) {
            return Optional.of(true);
        } else {
            return Optional.empty();
        }
    }

    private static Optional<Integer> getEmittingFrequencyFromConfiguration(Topics metricsTopic) {
        int frequency = Coerce.toInt(metricsTopic.find(EMITTING_FREQUENCY));

        if (frequency > 0) {
            return Optional.of(frequency);
        } else {
            return Optional.empty();
        }
    }
}
