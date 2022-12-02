/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.impl.MetricFactory;
import com.aws.greengrass.telemetry.models.TelemetryAggregation;
import com.aws.greengrass.telemetry.models.TelemetryUnit;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

public class ClientDeviceAuthMetrics {
    private final AtomicLong subscribeToCertificateUpdatesSuccess = new AtomicLong();
    private final MetricFactory mf = new MetricFactory(NAMESPACE);
    private final Clock clock;
    private static final String NAMESPACE = "ClientDeviceAuth";

    /**
     * Constructor for Client Device Auth Metrics.
     *
     * @param clock Clock
     */
    @Inject
    public ClientDeviceAuthMetrics(Clock clock) {
        this.clock = clock;
    }

    /**
     * Emit metrics using Metric Factory.
     */
    public void emitMetrics() {
        List<Metric> retrievedMetrics = collectMetrics();
        for (Metric retrievedMetric : retrievedMetrics) {
            mf.putMetricData(retrievedMetric);
        }
    }

    /**
     * Builds the CDA metrics.
     *
     * @return a list of {@link Metric}
     */
    public List<Metric> collectMetrics() {
        List<Metric> metricsList = new ArrayList<>();

        long timestamp = clock.instant().toEpochMilli();

        Metric metric = Metric.builder()
                .namespace(NAMESPACE)
                .name("SubscribeToCertificateUpdates.Success")
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(subscribeToCertificateUpdatesSuccess.getAndSet(0))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        return metricsList;
    }

    /**
     * Returns the clock.
     *
     * @return Clock
     */
    public Clock getClock() {
        return clock;
    }

    /**
     * Increments the Cert.SubscribeSuccess metric.
     */
    public void subscribeSuccess() {
        subscribeToCertificateUpdatesSuccess.incrementAndGet();
    }

    /**
     * Returns the Cert.SubscribeSuccess metric.
     */
    public long getSubscribeSuccess() {
        return SubscribeToCertificateUpdatesSuccess.get();
    }
}
