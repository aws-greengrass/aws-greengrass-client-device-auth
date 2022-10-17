/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.infra;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.VerifyThingAttachedToCertificateDTO;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyIotCertificate;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyThingAttachedToCertificate;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Pair;
import com.aws.greengrass.util.RetryUtils;
import software.amazon.awssdk.services.greengrassv2data.model.AccessDeniedException;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Periodically calls the VerifyIotCertificate to update the
 * locally cached certificates on a cadence.
 */
public class BackgroundCertificateRefresh implements Runnable {
    private final UseCases useCases;
    private final NetworkState networkState;
    private static final int DEFAULT_INTERVAL_SECONDS = 60 * 60; // 1H
    private static final Logger logger = LogManager.getLogger(BackgroundCertificateRefresh.class);
    private final ClientCertificateStore pemStore;
    private final ThingRegistry thingRegistry;
    private final IotAuthClient iotAuthClient;

    private ScheduledFuture<?> scheduledFuture = null;
    private final ScheduledThreadPoolExecutor scheduler;


    /**
     * Creates an instance of the BackgroundCertificateRefresh.A
     * @param scheduler - A ScheduledThreadPoolExecutor
     * @param thingRegistry - A thingRegistry
     * @param iotAuthClient - A client to interact with the IotCore
     * @param networkState - A network state
     * @param pemStore -  Store for the client certificates
     * @param useCases - useCases service
     */
    @Inject
    public BackgroundCertificateRefresh(
            ScheduledThreadPoolExecutor scheduler,
            ThingRegistry thingRegistry,
            NetworkState networkState,
            ClientCertificateStore pemStore,
            IotAuthClient iotAuthClient,
            UseCases useCases) {
        this.scheduler = scheduler;
        this.thingRegistry = thingRegistry;
        this.networkState = networkState;
        this.pemStore = pemStore;
        this.iotAuthClient = iotAuthClient;
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

        Consumer<Thing> consumer = refresh();

        getThingsAssociatedWithCoreDevice()
                .map(Thing::getThingName)
                .map(thingRegistry::getThing)
                .filter(Objects::nonNull)
                .forEach(consumer);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Stream<Thing> getThingsAssociatedWithCoreDevice() {
        RetryUtils.RetryConfig retryConfig = RetryUtils.RetryConfig.builder()
                .initialRetryInterval(Duration.ofSeconds(30)).maxRetryInterval(Duration.ofMinutes(3))
                .maxAttempt(3)
                .retryableExceptions(Arrays.asList(ThrottlingException.class, InternalServerException.class)).build();

        try {
            return RetryUtils.runWithRetry(retryConfig, iotAuthClient::getThingsAssociatedWithCoreDevice,
                    "get-things-associated-with-core-device", logger);
        } catch (AccessDeniedException e) {
            logger.atDebug().log(
                    "Access denied to list things associated with core device. Please make sure you "
                    + "have setup the correct permissions.");
        } catch (Exception e) {
            logger.warn("Failed to get things associated to the core device. Retry will be schedule later");
        }

        return Stream.empty();
    }

    private Consumer<Thing> refresh() {
        Set<String> alreadyRefreshed = new HashSet<>();

       return (Thing thing) -> {
           Set<String> certificateIds = thing.getAttachedCertificateIds().keySet();
           // Update thing certificate attachments
           certificateIds.stream()
                   .map(certificateId -> new VerifyThingAttachedToCertificateDTO(thing, new Certificate(certificateId)))
                   .forEach(this::refreshCertificateThingAttachment);

           // Update the certificate validity for certificates that have not been updated
           certificateIds.stream()
                   .filter((certificateId) -> !alreadyRefreshed.contains(certificateId))
                   .map((certificateId) -> new Pair<>(certificateId, pemStore.getPem(certificateId)))
                   .filter(pair -> pair.getRight().isPresent())
                   .forEach(pair -> {
                       this.refreshCertificateValidity(pair.getRight().get());
                       alreadyRefreshed.add(pair.getLeft());
                   });
       };
    }

    private void refreshCertificateThingAttachment(VerifyThingAttachedToCertificateDTO dto) {
        VerifyThingAttachedToCertificate useCase = useCases.get(VerifyThingAttachedToCertificate.class);
        useCase.apply(dto);
    }

    private void refreshCertificateValidity(String pem) {
        VerifyIotCertificate useCase = useCases.get(VerifyIotCertificate.class);
        useCase.apply(pem);
    }

    private boolean isNetworkDown() {
        return networkState.getConnectionStateFromMqtt() == NetworkState.ConnectionState.NETWORK_DOWN;
    }
}
