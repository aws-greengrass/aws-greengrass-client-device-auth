/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import lombok.Getter;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class MetricsEmitter {
    @Getter
    private ScheduledFuture<?> future;
    private final ScheduledExecutorService ses;
    private final Object emitMetricsLock = new Object();
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
     * Cancel previous task and start emitting metrics with no initial delay.
     *
     * @param periodicAggregateIntervalSec Periodic aggregate interval in seconds
     */
    public void start(int periodicAggregateIntervalSec) {
        synchronized (emitMetricsLock) {
            // Cancel previously running task
            stop();
            future = ses.scheduleWithFixedDelay(metrics::emitMetrics, 0, periodicAggregateIntervalSec,
                    TimeUnit.SECONDS);
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