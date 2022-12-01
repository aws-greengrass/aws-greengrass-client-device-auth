/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.impl.MetricFactory;
import com.aws.greengrass.telemetry.models.TelemetryAggregation;
import com.aws.greengrass.telemetry.models.TelemetryUnit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ClientDeviceAuthMetrics {
    private final AtomicLong SubscribeToCertificateUpdatesSuccess = new AtomicLong();
    private final MetricFactory mf = new MetricFactory(NAMESPACE);
    private static final String NAMESPACE = "ClientDeviceAuth";

    public void emitMetrics() {
        List<Metric> retrievedMetrics = collectMetrics();
        for (Metric retrievedMetric : retrievedMetrics) {
            mf.putMetricData(retrievedMetric);
        }
    }

    /**
     * Builds the CDA metrics.
     * @return a list of {@link Metric}
     */
    public List<Metric> collectMetrics() {
        List<Metric> metricsList = new ArrayList<>();

        long timestamp = Instant.now().toEpochMilli();

        Metric metric = Metric.builder()
                .namespace(NAMESPACE)
                .name("Cert.SubscribeSuccess")
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(SubscribeToCertificateUpdatesSuccess.getAndSet(0))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        return metricsList;
    }

    /**
     * Increments the Cert.SubscribeSuccess metric.
     */
    public void subscribeSuccess() {
        SubscribeToCertificateUpdatesSuccess.incrementAndGet();
    }

    /**
     * Returns the Cert.SubscribeSuccess metric.
     */
    public long getSubscribeSuccess() {
        return SubscribeToCertificateUpdatesSuccess.get();
    }
}
