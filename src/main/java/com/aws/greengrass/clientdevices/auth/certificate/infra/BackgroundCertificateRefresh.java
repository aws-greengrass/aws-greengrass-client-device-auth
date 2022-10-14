/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.infra;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.VerifyThingAttachedToCertificateDTO;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyIotCertificate;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyThingAttachedToCertificate;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * Periodically calls the VerifyIotCertificate to update the
 * locally cached certificates on a cadence.
 */
public class BackgroundCertificateRefresh implements Runnable {
    private final CertificateRegistry certificateRegistry;
    private final UseCases useCases;
    private final NetworkState networkState;
    private static final int DEFAULT_INTERVAL_SECONDS = 60 * 60; // 1H
    private static final Logger logger = LogManager.getLogger(BackgroundCertificateRefresh.class);
    private final ClientCertificateStore pemStore;
    private final ThingRegistry thingRegistry;

    private ScheduledFuture<?> scheduledFuture = null;
    private final ScheduledThreadPoolExecutor scheduler;


    /**
     * Creates an instance of the BackgroundCertificateRefresh.A
     * @param scheduler - A ScheduledThreadPoolExecutor
     * @param certificateRegistry - A certificate registry
     * @param thingRegistry - A thingRegistry
     * @param networkState - A network state
     * @param pemStore -  Store for the client certificates
     * @param useCases - useCases service
     */
    @Inject
    public BackgroundCertificateRefresh(
            ScheduledThreadPoolExecutor scheduler, CertificateRegistry certificateRegistry,
            ThingRegistry thingRegistry,  NetworkState networkState, ClientCertificateStore pemStore,
            UseCases useCases) {
        this.scheduler = scheduler;
        this.certificateRegistry = certificateRegistry;
        this.thingRegistry = thingRegistry;
        this.networkState = networkState;
        this.pemStore = pemStore;
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
        if (scheduledFuture != null) {
           return;
        }

        logger.info("Starting background refresh of client certificates every {} seconds", intervalSeconds);
        scheduledFuture =
            scheduler.scheduleAtFixedRate(this, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

    }

    /**
     * Stops the task if it has already been started.
     */
    @SuppressWarnings("PMD.NullAssignment")
    public void stop() {
        if (scheduledFuture == null) {
            return;
        }

        this.scheduledFuture.cancel(true);
        scheduledFuture = null;
    }

    /**
     * Return true if the background refresh has started.
     */
    public boolean isRunning() {
        return scheduledFuture != null;
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

        Arrays.stream(certificateRegistry.getAllCertificateIds())
                .filter(pemStore::exists)
                .map(pemStore::getPem)
                .forEach((pem) -> {
                    refreshCertificateValidity(pem.get());
                    refreshCertificateThingAttachment(pem.get());
                });
    }

    private void refreshCertificateThingAttachment(String pem) {
        try {
            Certificate certificate = Certificate.fromPem(pem);

            thingRegistry.getThingsWithCertificatePemAttached(certificate.getCertificateId())
                    .forEach(thing -> refreshCertificateThingAttachment(thing, certificate));
        } catch (InvalidCertificateException e) {
            logger.warn("Failed to refresh certificate thing attachment", e);
        }
    }

    private void refreshCertificateThingAttachment(Thing thing, Certificate certificate) {
        VerifyThingAttachedToCertificate useCase = useCases.get(VerifyThingAttachedToCertificate.class);

        try {
            useCase.apply(new VerifyThingAttachedToCertificateDTO(thing, certificate));
        } catch (AuthenticationException e) {
            logger.warn("Failed to refresh certificate thing attachment {}", thing.getThingName(), e);
        }
    }

    private void refreshCertificateValidity(String pem) {
        VerifyIotCertificate useCase = useCases.get(VerifyIotCertificate.class);
        useCase.apply(pem);
    }


    private boolean isNetworkDown() {
        return networkState.getConnectionStateFromMqtt() == NetworkState.ConnectionState.NETWORK_DOWN;
    }
}
