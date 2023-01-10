/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.builtin.services.telemetry.ComponentMetricIPCEventStreamAgent;
import lombok.Getter;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractPutComponentMetricOperationHandler;
import software.amazon.awssdk.aws.greengrass.model.PutComponentMetricRequest;
import software.amazon.awssdk.aws.greengrass.model.PutComponentMetricResponse;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class MetricsEmitter {
    @Getter
    private ScheduledFuture<?> future;
    private final ComponentMetricIPCEventStreamAgent componentMetricIPCAgent;
    private final OperationContinuationHandlerContext context;
    private final ScheduledExecutorService ses;
    private final Object emitMetricsLock = new Object();
    private final ClientDeviceAuthMetrics metrics;
    private boolean unauthorized = false;

    /**
     * Constructor for the MetricsEmitter.
     *
     * @param ses                     {@link ScheduledExecutorService}
     * @param metrics                 {@link ClientDeviceAuthMetrics}
     * @param componentMetricIPCAgent Component Metric IPC Event Stream Agent
     * @param context                 Operation continuation handler
     */
    @Inject
    public MetricsEmitter(ScheduledExecutorService ses, ClientDeviceAuthMetrics metrics,
                          ComponentMetricIPCEventStreamAgent componentMetricIPCAgent,
                          OperationContinuationHandlerContext context) {
        this.ses = ses;
        this.metrics = metrics;
        this.componentMetricIPCAgent = componentMetricIPCAgent;
        this.context = context;
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
            future = ses.scheduleWithFixedDelay(this::emitMetrics, 0, periodicAggregateIntervalSec,
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

    @SuppressWarnings("PMD.CloseResource")
    private PutComponentMetricResponse emitMetrics() {
        if (unauthorized) {
            //Avoid trying to repeatedly publish when unauthorized
            stop();
            return new PutComponentMetricResponse();
        }

        PutComponentMetricRequest request = new PutComponentMetricRequest();
        request.setMetrics(metrics.collectMetrics());
        GeneratedAbstractPutComponentMetricOperationHandler handler =
                componentMetricIPCAgent.getPutComponentMetricHandler(context);
        try {
            PutComponentMetricResponse response = handler.handleRequest(request);
            handler.closeStream();
            return response;
        } catch (UnauthorizedError e) {
            handler.closeStream();
            unauthorized = true;
            throw e;
        } catch (Error e) {
            handler.closeStream();
            throw e;
        }
    }
}