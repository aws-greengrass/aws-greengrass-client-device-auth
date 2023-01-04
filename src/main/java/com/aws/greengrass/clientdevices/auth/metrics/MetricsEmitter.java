/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.builtin.services.telemetry.ComponentMetricIPCEventStreamAgent;
import com.aws.greengrass.ipc.PutComponentMetricOperationHandler;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractPutComponentMetricOperationHandler;
import software.amazon.awssdk.aws.greengrass.model.Metric;
import software.amazon.awssdk.aws.greengrass.model.PutComponentMetricRequest;
import software.amazon.awssdk.aws.greengrass.model.PutComponentMetricResponse;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class MetricsEmitter {
    private ScheduledFuture<?> future;
    private final ScheduledExecutorService ses;
    private final Object emitMetricsLock = new Object();
    private final ClientDeviceAuthMetrics metrics;
    private final PutComponentMetricOperationHandler putComponentMetricOperationHandler;

    /**
     * Constructor for the MetricsEmitter.
     *
     * @param ses     {@link ScheduledExecutorService}
     * @param metrics {@link ClientDeviceAuthMetrics}
     */
    @Inject
    public MetricsEmitter(ScheduledExecutorService ses, ClientDeviceAuthMetrics metrics,
                          PutComponentMetricOperationHandler putComponentMetricOperationHandler) {
        this.ses = ses;
        this.metrics = metrics;
        this.putComponentMetricOperationHandler = putComponentMetricOperationHandler;
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
            PutComponentMetricRequest request = new PutComponentMetricRequest();
            request.setMetrics(metrics.collectMetrics());
            future = ses.scheduleWithFixedDelay((Runnable) putComponentMetricOperationHandler.handleRequest(request),
                    0, periodicAggregateIntervalSec, TimeUnit.SECONDS);
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