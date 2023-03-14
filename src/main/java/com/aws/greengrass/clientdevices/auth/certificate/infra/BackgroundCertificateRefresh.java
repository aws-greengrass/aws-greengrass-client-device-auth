/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.infra;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.VerifyThingAttachedToCertificateDTO;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.clientdevices.auth.iot.usecases.LocalVerificationException;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyIotCertificate;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyThingAttachedToCertificate;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.RetryUtils;
import software.amazon.awssdk.services.greengrassv2.model.AccessDeniedException;
import software.amazon.awssdk.services.greengrassv2.model.AssociatedClientDevice;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Periodically updates the certificates and its relationships to things (whether they are still attached or not to a
 * thing) to keep them in sync with the cloud.
 */
public class BackgroundCertificateRefresh implements Runnable, Consumer<NetworkStateProvider.ConnectionState> {
    private final UseCases useCases;
    private final NetworkStateProvider networkState;
    private static final int DEFAULT_INTERVAL_SECONDS = 60 * 60 * 24; // Once a day
    private static final Logger logger = LogManager.getLogger(BackgroundCertificateRefresh.class);
    private final ClientCertificateStore pemStore;
    private final ThingRegistry thingRegistry;
    private final IotAuthClient iotAuthClient;
    private final CertificateRegistry certificateRegistry;

    private ScheduledFuture<?> scheduledFuture = null;
    private final ScheduledThreadPoolExecutor scheduler;
    private final AtomicReference<Instant> nextScheduledRun = new AtomicReference<>();
    private final AtomicReference<Instant> lastRan = new AtomicReference<>();


    /**
     * Creates an instance of the BackgroundCertificateRefresh.
     *
     * @param scheduler           - A ScheduledThreadPoolExecutor
     * @param thingRegistry       - A thingRegistry
     * @param certificateRegistry - A certificateRegistry
     * @param iotAuthClient       - A client to interact with the IotCore
     * @param networkState        - A network state
     * @param pemStore            -  Store for the client certificates
     * @param useCases            - useCases service
     */
    @Inject
    public BackgroundCertificateRefresh(ScheduledThreadPoolExecutor scheduler, ThingRegistry thingRegistry,
                                        NetworkStateProvider networkState, CertificateRegistry certificateRegistry,
                                        ClientCertificateStore pemStore, IotAuthClient iotAuthClient,
                                        UseCases useCases) {
        this.scheduler = scheduler;
        this.thingRegistry = thingRegistry;
        this.networkState = networkState;
        this.certificateRegistry = certificateRegistry;
        this.pemStore = pemStore;
        this.iotAuthClient = iotAuthClient;
        this.useCases = useCases;
    }

    /**
     * Start running the task every DEFAULT_INTERVAL_SECONDS.
     */
    public void start() {
        if (scheduledFuture != null) {
            return;
        }

        logger.info("Starting background refresh of client certificates every {} seconds", DEFAULT_INTERVAL_SECONDS);
        scheduleNextRun();
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
     * Returns the next Instant the task is scheduled to run. This will change based on whether the device goes offline
     * or not but the guarantee is that it is scheduled to run 24h after the last successful run.
     */
    public Instant getNextScheduledRun() {
        return nextScheduledRun.get();
    }

    /**
     * Returns the last Instant the task ran successfully.
     */
    public Instant getLastRan() {
        return lastRan.get();
    }

    /**
     * Runs verifyIotCertificate useCase for all the registered client certificate PEMs.
     */
    @Override
    public synchronized void run() {
        if (isNetworkDown()) {
            logger.debug("Network is down - not refreshing certificates");
            return;
        }

        if (!canRun()) {
            return;
        }

        logger.info("Running background task: Refreshing client certificates");

        Optional<Set<String>> thingNamesAssociatedWithCore = getThingsAssociatedWithCoreDevice();
        thingNamesAssociatedWithCore.ifPresent(thingNames -> {
            this.refresh(thingNames);
            lastRan.set(Instant.now());
        });
        this.scheduleNextRun();
    }

    private void scheduleNextRun() {
        stop();

        Instant now = Instant.now();
        nextScheduledRun.set(now.plus(Duration.ofSeconds(DEFAULT_INTERVAL_SECONDS)));
        Duration duration = Duration.between(now, nextScheduledRun.get());

        scheduledFuture = scheduler.schedule(this, duration.getSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Handler to react to network changes.
     *
     * @param connectionState - A network state
     */
    @Override
    public void accept(NetworkStateProvider.ConnectionState connectionState) {
        if (connectionState == NetworkStateProvider.ConnectionState.NETWORK_UP) {
            run();
        }
    }

    private boolean canRun() {
        if (nextScheduledRun.get() == null) {
            return false;
        }

        Instant now = Instant.now();
        return now.equals(nextScheduledRun.get()) || now.isAfter(nextScheduledRun.get());
    }

    /**
     * Returns ThingAssociations which has information about what Things are associated with the core device and which
     * things are no longer associated with the core device.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Optional<Set<String>> getThingsAssociatedWithCoreDevice() {
        RetryUtils.RetryConfig retryConfig =
                RetryUtils.RetryConfig.builder().initialRetryInterval(Duration.ofSeconds(30))
                        .maxRetryInterval(Duration.ofMinutes(3)).maxAttempt(3)
                        .retryableExceptions(Arrays.asList(ThrottlingException.class, InternalServerException.class))
                        .build();

        try {
            Stream<List<AssociatedClientDevice>> cloudAssociatedDevices =
                    RetryUtils.runWithRetry(retryConfig, iotAuthClient::getThingsAssociatedWithCoreDevice,
                            "get-things-associated-with-core-device", logger);

            Set<String> cloudThings =
                    cloudAssociatedDevices.flatMap(List::stream).map(AssociatedClientDevice::thingName)
                            .collect(Collectors.toSet());

            return Optional.of(cloudThings);
        } catch (AccessDeniedException e) {
            logger.atInfo().log(
                "Did not refresh local certificates. To enable certificate refresh add a policy to the core device"
                        + " that grants the greengrass:ListClientDevicesAssociatedWithCoreDevice permission");
        } catch (Exception e) {
            logger.atWarn().cause(e)
                    .log("Failed to get things associated to the core device. Retry will be scheduled later");
        }

        return Optional.empty();
    }

    private void refresh(Set<String> thingNamesAttachedToCore) {
        Set<String> certificatesAttachedToThings = new HashSet<>();

        for (String thingName : thingNamesAttachedToCore) {
            Set<String> certificateIdAttachedToThing = this.refreshCertificateAttachments(thingName);
            certificatesAttachedToThings.addAll(certificateIdAttachedToThing);
        }

        for (String certificateId : certificatesAttachedToThings) {
            this.refreshCertificateValidity(certificateId);
        }

        // Clean up the registries by providing the names of the things that should still be attached and
        // the ids of the certificates that are still attached to a thing.
        cleanUpRegistries(thingNamesAttachedToCore, certificatesAttachedToThings);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Set<String> refreshCertificateAttachments(String thingName) {
        Thing thing = thingRegistry.getThing(thingName);

        if (Objects.isNull(thing)) {
            logger.atDebug().kv("thingName", thingName)
                    .log("No local version found for thing. Not refreshing thing certificate attachments");
            return Collections.emptySet();
        }

        Set<String> thingCertificateIds = thing.getAttachedCertificateIds().keySet();

        for (String certificateId : thingCertificateIds) {
            try {
                useCases.get(VerifyThingAttachedToCertificate.class)
                        .apply(new VerifyThingAttachedToCertificateDTO(thingName, certificateId));
            } catch (RuntimeException | LocalVerificationException e) {
                logger.atWarn().cause(e).kv("thingName", thing.getThingName()).kv("certificate", certificateId)
                        .log("Failed to verify thing certificate - certificate pem is invalid");
            }
        }

        return thingCertificateIds;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void refreshCertificateValidity(String certificateId) {
        Optional<String> certPem = null;

        try {
            certPem = pemStore.getPem(certificateId);
        } catch (IOException e) {
            logger.atWarn().cause(e).kv("certificateId", certificateId)
                    .log("Unable to load certificate. Certificate validity information will not be refreshed");
            return;
        }

        if (!certPem.isPresent()) {
            logger.atWarn().kv("certificateId", certificateId)
                    .log("Attempted to refresh certificate validity but its pem was not found");
            return;
        }

        try {
            useCases.get(VerifyIotCertificate.class).apply(certPem.get());
        } catch (RuntimeException e) {
            logger.atWarn().kv("certificateId", certificateId).cause(e).log("Failed to verify certificate validity");
        }
    }

    private void cleanUpRegistries(Set<String> attachedThings, Set<String> certificatesAttachedToThings) {
        Stream<Thing> localThings = thingRegistry.getAllThings();
        Stream<Certificate> localCerts = certificateRegistry.getAllCertificates();

        localThings.filter(thing -> !attachedThings.contains(thing.getThingName())).forEach(thingRegistry::deleteThing);

        localCerts.filter(certificate -> !certificatesAttachedToThings.contains(certificate.getCertificateId()))
                .forEach(certificateRegistry::deleteCertificate);
    }

    private boolean isNetworkDown() {
        return networkState.getConnectionState() == NetworkStateProvider.ConnectionState.NETWORK_DOWN;
    }
}
