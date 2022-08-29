/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class RegistryRefreshScheduler {

    private final List<Runnable> refreshRunnable = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService ses;
    private ScheduledFuture<?> scheduledFuture;

    /**
     * Constructor.
     *
     * @param ses ScheduledExecutorService for refresh
     */
    @Inject
    public RegistryRefreshScheduler(ScheduledExecutorService ses) {
        this.ses = ses;
    }

    /**
     * Schedules the runnable to be run at configured interval.
     *
     * @param runnable Runnable registry refresh
     */
    public void schedule(Runnable runnable) {
        refreshRunnable.add(runnable);
    }

    /**
     * Starts registry refresh scheduler.
     */
    public void startScheduler() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }

        scheduledFuture = ses.scheduleAtFixedRate(this::runRefresh, RegistryConfig.REGISTRY_REFRESH_FREQUENCY_SECONDS,
                RegistryConfig.REGISTRY_REFRESH_FREQUENCY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Stop registry refresh monitor.
     */
    public void stopScheduler() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    private void runRefresh() {
        refreshRunnable.forEach(Runnable::run);
    }
}
