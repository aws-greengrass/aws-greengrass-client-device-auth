/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.clientdevices.auth.configuration.MetricsConfiguration;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class MetricsEmitter {
    private ScheduledFuture<?> future;
    private final ScheduledExecutorService ses;
    private final Object emitMetricsLock = new Object();
    private static final int DEFAULT_PERIODIC_AGGREGATE_INTERVAL_SEC = 3_600;
    private int periodicAggregateIntervalSec;
    private boolean metricsEnabled = true;
    private final ClientDeviceAuthMetrics metrics;

    /**
     * Constructor for the MetricsEmitter.
     *
     * @param ses                  {@link ScheduledExecutorService}
     * @param metrics              {@link ClientDeviceAuthMetrics}
     * @param metricsConfiguration {@link MetricsConfiguration}
     */
    @Inject
    public MetricsEmitter(ScheduledExecutorService ses, ClientDeviceAuthMetrics metrics,
                          MetricsConfiguration metricsConfiguration) {
        this(ses, metrics, metricsConfiguration, DEFAULT_PERIODIC_AGGREGATE_INTERVAL_SEC);
    }

    private MetricsEmitter(ScheduledExecutorService ses, ClientDeviceAuthMetrics metrics,
                           MetricsConfiguration metricsConfiguration, int periodicAggregateIntervalSec) {
        this.ses = ses;
        this.metrics = metrics;

        if (!metricsConfiguration.getEnableMetrics().isPresent()) {
            metricsEnabled = false;
        }

        if (metricsEnabled && metricsConfiguration.getEmittingFrequency().isPresent()) {
            this.periodicAggregateIntervalSec = metricsConfiguration.getEmittingFrequency().get();
        } else {
            this.periodicAggregateIntervalSec = periodicAggregateIntervalSec;
        }
    }

    /**
     * Start emitting metrics with no initial delay unless specified otherwise by the Metrics Configuration.
     */
    public void start() {
        if (metricsEnabled) {
            synchronized (emitMetricsLock) {
                // Cancel previously running task
                stop();

                future = ses.scheduleWithFixedDelay(metrics::emitMetrics, 0, periodicAggregateIntervalSec,
                        TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Stop emitting metrics.
     */
    public void stop() {
        if (metricsEnabled) {
            synchronized (emitMetricsLock) {
                if (future != null) {
                    future.cancel(true);
                }
            }
        }
    }
}