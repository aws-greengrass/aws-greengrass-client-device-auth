/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;


import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.models.TelemetryAggregation;
import com.aws.greengrass.telemetry.models.TelemetryUnit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ClientDeviceAuthMetrics {

    private final AtomicLong certSubscribeSuccess = new AtomicLong();
    private final AtomicLong certSubscribeError = new AtomicLong();
    private final AtomicLong invalidConfig = new AtomicLong();
    private final AtomicLong certRotation = new AtomicLong();
    private final String NAMESPACE = "ClientDeviceAuth";

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
                .value(certSubscribeSuccess.getAndSet(0))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name("Cert.SubscribeError")
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(certSubscribeError.getAndSet(0))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name("Config.Invalid")
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(invalidConfig.getAndSet(0))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name("Cert.Rotate")
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(certRotation.getAndSet(0))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        return metricsList;
    }

    /**
     * Increments the Cert.SubscribeSuccess metric.
     */
    public void subscribeSuccess() {
        certSubscribeSuccess.incrementAndGet();
    }

    /**
     * Returns the Cert.SubscribeSuccess metric.
     */
    public long getSubscribeSuccess() { return certSubscribeSuccess.get(); }

    /**
     * Increments the Cert.SubscribeError metric.
     */
    public void subscribeError() { certSubscribeError.incrementAndGet(); }

    /**
     * Returns the Cert.SubscribeError metric.
     */
    public long getSubscribeError() { return certSubscribeError.get(); }

    /**
     * Increments the Config.Invalid metric.
     */
    public void invalidConfig() { invalidConfig.incrementAndGet(); }

    /**
     * Returns the Config.Invalid metric.
     */
    public long getInvalidConfig() { return invalidConfig.get(); }

    /**
     * Increments the Cert.Rotate metric.
     */
    public void certRotation() { certRotation.incrementAndGet(); }

    /**
     * Returns the Cert.Rotate metric.
     */
    public long getCertRotation() { return certRotation.get(); }
}
