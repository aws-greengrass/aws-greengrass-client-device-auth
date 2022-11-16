/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.telemetry.PeriodicMetricsEmitter;
import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.impl.MetricFactory;

import java.util.ArrayList;
import java.util.List;

public class MetricsEmitter extends PeriodicMetricsEmitter {
    private static final String NAMESPACE = "ClientDeviceAuth";
    private final MetricFactory mf = new MetricFactory(NAMESPACE);

    /**
     * Emit CDA metrics.
     */
    @Override
    public void emitMetrics() {
        List<Metric> retrievedMetrics = getMetrics();
        for (Metric retrievedMetric: retrievedMetrics) {
            mf.putMetricData(retrievedMetric);
        }
    }

    /**
     * Retrieve CDA metrics.
     * @return a list of {@link Metric}
     */
    @Override
    public List<Metric> getMetrics() {
        //implementation pending
        return new ArrayList<>();
    }
}
