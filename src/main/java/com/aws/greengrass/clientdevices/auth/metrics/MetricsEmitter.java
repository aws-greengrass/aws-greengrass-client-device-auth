/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.telemetry.PeriodicMetricsEmitter;
import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.impl.MetricFactory;

import java.util.List;
import javax.inject.Inject;

public class MetricsEmitter extends PeriodicMetricsEmitter{
    public static final Logger logger = LogManager.getLogger(MetricsEmitter.class);
    public static final String NAMESPACE = "ClientDeviceAuth";
    private final MetricFactory mf = new MetricFactory(NAMESPACE);

    /**
     * Constructor for metrics emitter
     */
    @Inject
    public MetricsEmitter() {
        super();
    }

    /**
     * Emit CDA metrics
     */
    @Override
    public void emitMetrics() {
        List<Metric> retrievedMetrics = getMetrics();
        for(Metric retrievedMetric: retrievedMetrics) {
            mf.putMetricData(retrievedMetric);
        }
    }

    /**
     * Retrieve CDA metrics
     * @return a list of {@link Metric}
     * 
     * implementation pending
     */
    @Override
    public List<Metric> getMetrics() {
        return null; 
    }
}
