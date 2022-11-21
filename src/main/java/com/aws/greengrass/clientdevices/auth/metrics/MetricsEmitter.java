/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.telemetry.PeriodicMetricsEmitter;
import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.impl.MetricFactory;

import java.util.List;
import javax.inject.Inject;

public class MetricsEmitter extends PeriodicMetricsEmitter {
    private static final String NAMESPACE = "ClientDeviceAuth";
    private final MetricFactory mf = new MetricFactory(NAMESPACE);
    private final ClientDeviceAuthMetrics metrics;

    /**
     * Constructor for metrics emitter.
     *
     * @param metrics {@link ClientDeviceAuthMetrics}
     */
    @Inject
    public MetricsEmitter(ClientDeviceAuthMetrics metrics) {
        super();
        this.metrics = metrics;
    }

    /**
     * Emit CDA metrics.
     */
    @Override
    public void emitMetrics() {
        List<Metric> retrievedMetrics = getMetrics();
        for (Metric retrievedMetric : retrievedMetrics) {
            mf.putMetricData(retrievedMetric);
        }
    }

    /**
     * Retrieve CDA metrics.
     *
     * @return a list of {@link Metric}
     */
    @Override
    public List<Metric> getMetrics() {
        return metrics.collectMetrics();
    }
}
