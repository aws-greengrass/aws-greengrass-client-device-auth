/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;


import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class RegistryRefreshMonitor {

    private final Set<RefreshableRegistry> monitoredRegistry = new CopyOnWriteArraySet<>();
    private final ScheduledExecutorService ses;
    private ScheduledFuture<?> monitorFuture;

    /**
     * Constructor.
     *
     * @param ses ScheduledExecutorService for monitor
     */
    @Inject
    public RegistryRefreshMonitor(ScheduledExecutorService ses) {
        this.ses = ses;
    }

    /**
     * Add a Refreshable registry to the monitor.
     *
     * @param registry Refreshable registry
     */
    public void addToMonitor(RefreshableRegistry registry) {
        monitoredRegistry.add(registry);
    }

    /**
     * Start Registry monitor.
     */
    public void startMonitor() {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }
        monitorFuture = ses.scheduleAtFixedRate(this::refreshRegistry,
                RegistryConfig.REGISTRY_REFRESH_FREQUENCY_SECONDS, RegistryConfig.REGISTRY_REFRESH_FREQUENCY_SECONDS,
                TimeUnit.SECONDS);
    }

    /**
     * Stop registry refresh monitor.
     */
    public void stopMonitor() {
        if (monitorFuture != null) {
            monitorFuture.cancel(true);
        }
    }

    private void refreshRegistry() {
        monitoredRegistry.forEach(RefreshableRegistry::refresh);
    }

}
