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
import com.aws.greengrass.util.RetryUtils;
import software.amazon.awssdk.services.greengrassv2data.model.AccessDeniedException;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Periodically updates the certificates and its relationships to things
 * (whether they are still attached or not to a thing) to keep them in sync with the cloud.
 */
public class BackgroundCertificateRefresh implements Runnable {
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

        ThingAssociations associations = getThingsAssociatedWithCoreDevice();
        refresh(associations);
    }

    /**
     * Returns ThingAssociations which has information about what Things are associated with the core device and
     * which things are no longer associated with the core device.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private ThingAssociations getThingsAssociatedWithCoreDevice() {
        ThingAssociations associations = new ThingAssociations();
        RetryUtils.RetryConfig retryConfig = RetryUtils.RetryConfig.builder()
                .initialRetryInterval(Duration.ofSeconds(30)).maxRetryInterval(Duration.ofMinutes(3))
                .maxAttempt(3)
                .retryableExceptions(Arrays.asList(ThrottlingException.class, InternalServerException.class)).build();

        try {
            Set<String> cloudAttachedThingNames =  RetryUtils.runWithRetry(
                    retryConfig, iotAuthClient::getThingsAssociatedWithCoreDevice,
                            "get-things-associated-with-core-device", logger)
                    .map(Thing::getThingName).collect(Collectors.toSet());

            thingRegistry.getAllThings().forEach(thing -> {
                if (cloudAttachedThingNames.contains(thing.getThingName()))  {
                    associations.attached(thing);
                } else {
                    associations.detached(thing);
                }
            });
        } catch (AccessDeniedException e) {
            logger.atWarn().cause(e).log(
                    "Access denied to list things associated with core device. Please make sure you "
                    + "have setup the correct permissions.");
        } catch (Exception e) {
            logger.warn("Failed to get things associated to the core device. Retry will be schedule later");
        }

        return associations;
    }

    private void refresh(ThingAssociations associations) {
       associations.getThingAssociations().forEach(this::refreshCertificateThingAttachment);
       associations.getCertificateAssociations().forEach(this::refreshCertificateValidity);
       associations.getStaleCertificateAssociations().forEach(certificateRegistry::deleteCertificate);
       associations.getStaleThingAssociations().forEach(thingRegistry::deleteThing);
    }

    private void refreshCertificateThingAttachment(Thing thing) {
        VerifyThingAttachedToCertificate useCase = useCases.get(VerifyThingAttachedToCertificate.class);

        thing.getAttachedCertificateIds().keySet().forEach(certificateId -> {
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
                useCase.apply(new VerifyThingAttachedToCertificateDTO(thing, certificate));
            } catch (InvalidCertificateException e) {
                logger.atWarn().cause(e).kv("thing", thing.getThingName()).kv("certificate", certificateId)
                        .log("Failed to verify thing attached to certificate");
            }
        });
    }

    private void refreshCertificateValidity(String certificateId) {
        Optional<String> certPem = pemStore.getPem(certificateId);

        if (!certPem.isPresent()) {
            logger.atWarn().kv("certificateId", certificateId)
                    .log("Tried to refresh certificate validity but its pem was not found");
            return;
        }

        VerifyIotCertificate useCase = useCases.get(VerifyIotCertificate.class);
        useCase.apply(certPem.get());
    }

    private boolean isNetworkDown() {
        return networkState.getConnectionStateFromMqtt() == NetworkState.ConnectionState.NETWORK_DOWN;
    }

    /**
     * Represents which Things are still attached to the core device and which things are no longer attached.
     */
    private static class ThingAssociations {
        private final Set<Thing> attachedThings = new HashSet<>();
        private final Set<Thing> detachedThings = new HashSet<>();
        private final Set<String> attachedCertificateIds = new HashSet<>();
        private  final Set<String> detachedCertificatesIds = new HashSet<>();

        /**
         * Marks a thing as attached to the core.
         * @param thing - A thing
         */
        public void attached(Thing thing) {
            attachedCertificateIds.addAll(thing.getAttachedCertificateIds().keySet());
            this.attachedThings.add(thing);
        }

        /**
         * Marks a thing as detached from the core.
         * @param thing - A thing
         */
        public void detached(Thing thing) {
            detachedCertificatesIds.addAll(thing.getAttachedCertificateIds().keySet());
            this.detachedThings.add(thing);
        }

        /**
         * Gets a stream of all the things that are still associated to the core.
         */
        public Stream<Thing> getThingAssociations() {
            return attachedThings.stream();
        }

        /**
         * Get a stream of all the certificate ids that are still attached to things.
         */
        public Stream<String> getCertificateAssociations() {
            return attachedCertificateIds.stream();
        }

        /**
         * Get a stream of all the certificate ids that are no longer associated to any Thing that is associated
         * to the core device.
         */
        public Stream<String> getStaleCertificateAssociations() {
            // Why this - there might be certificates attached to a thing being removed that are still attached
            // to another thing. In that situation we don't want to consider them stale
            return detachedCertificatesIds.stream().filter(id -> !attachedCertificateIds.contains(id));
        }

        /**
         * Get a stream of all the things that are no longer associated with the core device.
         */
        public Stream<Thing> getStaleThingAssociations() {
            return detachedThings.stream();
        }
    }
}
