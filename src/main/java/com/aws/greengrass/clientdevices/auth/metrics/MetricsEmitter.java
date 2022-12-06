/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class MetricsEmitter {
    private ScheduledFuture<?> future;
    private final ScheduledExecutorService ses;
    private final Object emitMetricsLock = new Object();
    private static final int DEFAULT_PERIODIC_AGGREGATE_INTERVAL_SEC = 3_600;
    private final ClientDeviceAuthMetrics metrics;


    /**
     * Constructor for the MetricsEmitter.
     *
     * @param ses     {@link ScheduledExecutorService}
     * @param metrics {@link ClientDeviceAuthMetrics}
     */
    @Inject
    public MetricsEmitter(ScheduledExecutorService ses, ClientDeviceAuthMetrics metrics) {
        this.ses = ses;
        this.metrics = metrics;
    }

    /**
     * Constructor for the MetricsEmitter when publish and aggregate intervals are provided.
     *
     * @param ses                                 {@link ScheduledExecutorService}
     * @param metrics                             {@link ClientDeviceAuthMetrics}
     * @param periodicPublishMetricsIntervalSec   interval for cadence based telemetry publish
     * @param periodicAggregateMetricsIntervalSec interval for cadence based telemetry metrics aggregation
     */
    public MetricsEmitter(ScheduledExecutorService ses, ClientDeviceAuthMetrics metrics,
                          int periodicPublishMetricsIntervalSec, int periodicAggregateMetricsIntervalSec) {
        this.ses = ses;
        this.metrics = metrics;
    }

    /**
     * Start emitting metrics with no initial delay.
     */
    public void start() {
        synchronized (emitMetricsLock) {
            // Cancel previously running task
            stop();

            future = ses.scheduleWithFixedDelay(metrics::emitMetrics, 0,
                    DEFAULT_PERIODIC_AGGREGATE_INTERVAL_SEC, TimeUnit.SECONDS);
        }
    }

    /**
     * Stop emitting metrics.
     */
    public void stop() {
        synchronized (emitMetricsLock) {
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}