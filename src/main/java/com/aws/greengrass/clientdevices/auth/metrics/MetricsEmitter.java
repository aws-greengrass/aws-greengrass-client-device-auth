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
    private final ClientDeviceAuthMetrics metrics;

    /**
     * Constructor for the MetricsEmitter.
     *
     * @param ses                  {@link ScheduledExecutorService}
     * @param metrics              {@link ClientDeviceAuthMetrics}
     */
    @Inject
    public MetricsEmitter(ScheduledExecutorService ses, ClientDeviceAuthMetrics metrics) {
        this.ses = ses;
        this.metrics = metrics;
    }

    /**
     * Restart the metrics emitter and apply configurations.
     *
     * @param disableMetrics               Enable metrics flag
     * @param periodicAggregateIntervalSec Periodic aggregate interval in seconds
     */
    public void restart(boolean disableMetrics, int periodicAggregateIntervalSec) {
        if (disableMetrics) {
            return;
        }

        stop();
        start(periodicAggregateIntervalSec);
    }

    /**
     * Start emitting metrics with no initial delay unless specified otherwise by the Metrics Configuration.
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