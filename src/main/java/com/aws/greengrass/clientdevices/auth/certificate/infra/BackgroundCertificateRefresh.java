/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.infra;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
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
import software.amazon.awssdk.services.greengrassv2.model.AccessDeniedException;
import software.amazon.awssdk.services.greengrassv2.model.AssociatedClientDevice;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
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
 * Periodically updates the certificates and its relationships to things
 * (whether they are still attached or not to a thing) to keep them in sync with the cloud.
 */
public class BackgroundCertificateRefresh implements Runnable, Consumer<NetworkState.ConnectionState> {
    private final UseCases useCases;
    private final NetworkState networkState;
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
     * @param scheduler - A ScheduledThreadPoolExecutor
     * @param thingRegistry - A thingRegistry
     * @param certificateRegistry - A certificateRegistry
     * @param iotAuthClient - A client to interact with the IotCore
     * @param networkState - A network state
     * @param pemStore -  Store for the client certificates
     * @param useCases - useCases service
     */
    @Inject
    public BackgroundCertificateRefresh(
            ScheduledThreadPoolExecutor scheduler, ThingRegistry thingRegistry, NetworkState networkState,
            CertificateRegistry certificateRegistry, ClientCertificateStore pemStore, IotAuthClient iotAuthClient,
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

        Optional<Stream<Thing>> associations = getThingsAssociatedWithCoreDevice();
        associations.ifPresent(a -> {
            this.refresh(a);
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
     * @param connectionState - A network state
     */
    @Override
    public void accept(NetworkState.ConnectionState connectionState) {
        if (connectionState == NetworkState.ConnectionState.NETWORK_UP) {
            run();
        }
    }

    private boolean canRun() {
        if (lastRan.get() == null) {
            return true;
        }

        Instant now = Instant.now();
        return now.equals(nextScheduledRun.get()) || now.isAfter(nextScheduledRun.get());
    }

    /**
     * Returns ThingAssociations which has information about what Things are associated with the core device and
     * which things are no longer associated with the core device.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Optional<Stream<Thing>> getThingsAssociatedWithCoreDevice() {
        RetryUtils.RetryConfig retryConfig = RetryUtils.RetryConfig.builder()
                .initialRetryInterval(Duration.ofSeconds(30)).maxRetryInterval(Duration.ofMinutes(3))
                .maxAttempt(3)
                .retryableExceptions(Arrays.asList(ThrottlingException.class, InternalServerException.class)).build();

        try {
            Stream<List<AssociatedClientDevice>> cloudAssociatedDevices = RetryUtils.runWithRetry(
                    retryConfig, iotAuthClient::getThingsAssociatedWithCoreDevice,
                            "get-things-associated-with-core-device", logger);

            Stream<Thing> cloudThings = cloudAssociatedDevices.flatMap(List::stream)
                        .map(AssociatedClientDevice::thingName)
                        .map(name -> Thing.of(name, Thing.Source.CLOUD));

            return Optional.of(cloudThings);
        } catch (AccessDeniedException e) {
            logger.atInfo().log(
                "Did not refresh local certificates. To enable certificate refresh add a policy to the core device"
                        + " that grants the greengrass:ListClientDevicesAssociatedWithCoreDevice permission");
        } catch (Exception e) {
            logger.atWarn().cause(e).log(
                    "Failed to get things associated to the core device. Retry will be scheduled later");
        }

        return Optional.empty();
    }

    private void refresh(Stream<Thing> thingsAttachedToCore) {
        Pair<Set<String>, Consumer<Thing>> refreshCertificates = refreshCertificateValidity();

        Set<String> attachedThingNames = thingsAttachedToCore
                // Refresh the thing certificate attachments
                .peek(this::refreshCertificateAttachments)
                // Load the thing from the local store after their certificates have been updated.
                .map(thing -> thingRegistry.getThing(thing.getThingName()))
                // Skip the things that could not be found or for which the refresh failed
                .filter(Objects::nonNull)
                // Refresh the validity of all the certificates attached to the things
                .peek(refreshCertificates.getRight())
                // Get the names of the valid things that should still be in the registries
                .map(Thing::getThingName)
                // Wait for the stream to finish processing, collect all the valid attached things
                .collect(Collectors.toSet());

        // Clean up the registries by providing the names of the things that should still be attached and
        // the ids of the certificates that are still attached to a thing.
        cleanUpRegistries(attachedThingNames, refreshCertificates.getLeft());
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void refreshCertificateAttachments(Thing thing) {
        AtomicReference<Thing> thingWithCerts = new AtomicReference<>(thing);

        if (thingWithCerts.get() == null) {
            logger.atDebug().kv("thingName", thing.getThingName())
                    .log("No local version found for thing. Not refreshing thing certificate attachments");
            return;
        }

        if (thingWithCerts.get().getSource() != Thing.Source.LOCAL) {
            thingWithCerts.set(thingRegistry.getThing(thing.getThingName()));
        }

        Set<String> thingCertificateIds = thingWithCerts.get().getAttachedCertificateIds().keySet();

        thingCertificateIds.forEach(certificateId -> {
            Optional<String> certPem = pemStore.getPem(certificateId);

            if (!certPem.isPresent()) {
                logger.atWarn()
                        .kv("certificateId", certificateId)
                        .kv("thing", thing.getThingName())
                        .log("Tried to refresh certificate thing attachment but its pem was not found");
                return;
            }

            try {
                Certificate certificate = Certificate.fromPem(certPem.get());
                useCases.get(VerifyThingAttachedToCertificate.class).apply(
                        new VerifyThingAttachedToCertificateDTO(thingWithCerts.get(), certificate));
            } catch (InvalidCertificateException | RuntimeException e) {
                logger.atWarn().cause(e).kv("thingName", thing.getThingName()).kv("certificate", certificateId)
                        .log("Failed to verify thing certificate - certificate pem is invalid");
            }
        });
    }

    /**
     * Closure around a Consumer that refreshes all the certificates attached to a thing. It records the certificates
     * that have already been refreshed to avoid refreshing them again if they are attached to another thing and allows
     * the enclosed value to be returned ba accessing the pair.getLeft()
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Pair<Set<String>, Consumer<Thing>> refreshCertificateValidity() {
        Set<String> alreadyRefreshed = new HashSet<>();
        VerifyIotCertificate verifyCertificate = useCases.get(VerifyIotCertificate.class);

        Consumer<Thing> callback = (Thing thing) -> {
            Set<String> thingCertificateIds = thing.getAttachedCertificateIds().keySet();

            thingCertificateIds.forEach(certificateId -> {

                if (alreadyRefreshed.contains(certificateId)) {
                    return;
                }

                Optional<String> certPem = pemStore.getPem(certificateId);

                if (!certPem.isPresent()) {
                    logger.atWarn()
                            .kv("certificateId", certificateId)
                            .kv("thing", thing.getThingName())
                            .log("Tried to refresh certificate validity but its pem was not found");
                    return;
                }

                alreadyRefreshed.add(certificateId);
                try {
                    verifyCertificate.apply(certPem.get());
                } catch (RuntimeException e) {
                logger.atWarn().cause(e)
                        .log("Failed to verify certificate validity");
            }
            });
        };

        return new Pair<>(alreadyRefreshed, callback);
    }

    private void cleanUpRegistries(Set<String> attachedThings, Set<String> certificatesAttachedToThings) {
        Stream<Thing> localThings = thingRegistry.getAllThings();
        Stream<Certificate> localCerts = certificateRegistry.getAllCertificates();

        localThings
                .filter(thing -> !attachedThings.contains(thing.getThingName()))
                .forEach(thingRegistry::deleteThing);

        localCerts
                .filter(certificate -> !certificatesAttachedToThings.contains(certificate.getCertificateId()))
                .forEach(certificateRegistry::deleteCertificate);
    }

    private boolean isNetworkDown() {
        return networkState.getConnectionStateFromMqtt() == NetworkState.ConnectionState.NETWORK_DOWN;
    }
}
