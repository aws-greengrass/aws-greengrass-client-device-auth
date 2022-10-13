/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.infra;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyIotCertificate;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;

/**
 * Periodically calls the VerifyIotCertificate to update the
 * locally cached certificates on a cadence.
 */
public class BackgroundCertificateRefresh implements Runnable {
    private final CertificateRegistry registry;
    private final UseCases useCases;
    private final NetworkState networkState;
    private static final int DEFAULT_INTERVAL_SECONDS = 60 * 60; // 1H
    private static final Logger logger = LogManager.getLogger(BackgroundCertificateRefresh.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicReference<ScheduledFuture<?>> scheduledFuture = new AtomicReference<>(null);


    /**
     * Creates an instance of the BackgroundCertificateRefresh.
     * @param certificateRegistry - A certificate registry
     * @param networkState - A network state
     * @param useCases - useCases service
     */
    @Inject
    public BackgroundCertificateRefresh(CertificateRegistry certificateRegistry, NetworkState networkState,
            UseCases useCases) {
        this.registry = certificateRegistry;
        this.networkState = networkState;
        this.useCases = useCases;
    }

    /**
     * Start running the task every DEFAULT_INTERVAL_SECONDS.
     */
    public void start() {
        start(DEFAULT_INTERVAL_SECONDS);
    }

    /**
     * Start running the task on every intervalSeconds.
     * @param intervalSeconds - frequency for this task to run
     */
    public void start(int intervalSeconds) {
        if (scheduledFuture.get() != null) {
           return;
        }

        logger.info("Starting background refresh of client certificates every {} seconds", intervalSeconds);
        scheduledFuture.set(
            scheduler.scheduleAtFixedRate(this, intervalSeconds, intervalSeconds, TimeUnit.SECONDS)
        );
    }

    /**
     * Stops the task if it has already been started.
     */
    public void stop() {
        if (scheduledFuture.get() == null) {
            return;
        }

        this.scheduledFuture.get().cancel(true);
        scheduledFuture.set(null);
    }

    /**
     * Return true if the background refresh has started.
     */
    public boolean isRunning() {
        return scheduledFuture.get() != null;
    }

    /**
     * Runs verifyIotCertificate useCase for all the registered client certificate PEMs.
     */
    @Override
    public void run() {
        if (isNetworkDown()) {
            logger.debug("Network is down - not refreshing certificates");
            return;
        }

        VerifyIotCertificate verifyIotCertificate = useCases.get(VerifyIotCertificate.class);
        registry.getAllCertificatePems().forEach(verifyIotCertificate::apply);
    }

    private boolean isNetworkDown() {
        return networkState.getConnectionStateFromMqtt() == NetworkState.ConnectionState.NETWORK_DOWN;
    }
}
