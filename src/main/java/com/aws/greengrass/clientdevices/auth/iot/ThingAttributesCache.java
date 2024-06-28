/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.dto.ThingAssociationV1DTO;
import com.aws.greengrass.clientdevices.auth.iot.dto.ThingDescriptionV1DTO;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import software.amazon.awssdk.services.greengrassv2.model.AssociatedClientDevice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class ThingAttributesCache {

    private static final Logger logger = LogManager.getLogger(ThingAttributesCache.class);

    private static final long DEFAULT_REFRESH_DELAY_SECONDS =
            TimeUnit.MINUTES.toSeconds(1);
    private static final long DEFAULT_THING_ASSOCIATION_TRUST_DURATION_SECONDS =
            TimeUnit.MINUTES.toSeconds(5);
    private static final long DEFAULT_THING_DESCRIPTION_TRUST_DURATION_SECONDS =
            TimeUnit.MINUTES.toSeconds(10);

    // set once during component install
    private static final AtomicReference<ThingAttributesCache> INSTANCE = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> initialized = new AtomicReference<>();

    private final IotCoreClient iotCoreClient;
    private final IotAuthClient iotAuthClient;

    private final Map<String, Map<String, String>> attributesByThing = new ConcurrentHashMap<>();

    private final ScheduledExecutorService ses;
    private final NetworkStateProvider networkStateProvider;
    private ScheduledFuture<?> refreshTask;

    private final RuntimeConfiguration runtimeConfiguration;

    public static Optional<ThingAttributesCache> instance() {
        return Optional.ofNullable(INSTANCE.get());
    }

    public static void setInstance(ThingAttributesCache cache) {
        INSTANCE.set(cache);
    }

    /**
     * Construct a ThingAttributesCache.
     *
     * @param iotCoreClient        iot core client
     * @param iotAuthClient        iot auth client
     * @param networkStateProvider network state provider
     * @param runtimeConfiguration runtime configuration
     * @param ses                  scheduled executor service
     */
    @Inject
    public ThingAttributesCache(IotCoreClient iotCoreClient,
                                IotAuthClient iotAuthClient,
                                NetworkStateProvider networkStateProvider,
                                RuntimeConfiguration runtimeConfiguration,
                                ScheduledExecutorService ses) {
        this.iotCoreClient = iotCoreClient;
        this.iotAuthClient = iotAuthClient;
        this.networkStateProvider = networkStateProvider;
        this.runtimeConfiguration = runtimeConfiguration;
        this.ses = ses;
    }

    /**
     * Can be called after {@link ThingAttributesCache#startPeriodicRefresh} in order to block
     * until this class retrieves thing attributes. If attributes have already been loaded, this will return
     * immediately.
     *
     * @param time time to wait
     * @param unit time unit
     * @return true if initialized
     * @throws InterruptedException interrupted while waiting
     */
    public boolean waitForInitialization(long time, TimeUnit unit) throws InterruptedException {
        CountDownLatch latch = initialized.get();
        if (latch == null) {
            return false;
        }
        return latch.await(time, unit);
    }

    private void resetInitialized() {
        initialized.set(new CountDownLatch(1));
    }

    private void markAsInitialized() {
        CountDownLatch latch = initialized.get();
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * Refresh client device thing attributes from cloud, periodically.
     */
    public void startPeriodicRefresh() {
        stopPeriodicRefresh();
        // TODO pull from configuration
        refreshTask = ses.scheduleWithFixedDelay(this::refresh, 0L,
                DEFAULT_REFRESH_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Stop the client device thing attribute refresh process.
     */
    public void stopPeriodicRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(true);
        }
        resetInitialized();
    }

    private void refresh() {
        logger.atTrace().log("beginning thing-attribute cache refresh");
        getAssociatedThingNames().ifPresent(thingNames -> {
            for (String thingName : thingNames) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                getThingAttributes(thingName).ifPresent(attrs -> {
                    logger.atInfo().kv("thing", thingName).log("attributes refreshed for device");
                    attributesByThing.put(thingName, new ConcurrentHashMap<>(attrs));
                });
            }
            // TODO it's currently possible that not all thing attributes were successfully
            //      fetched at this point, meaning that CDA will have started and devices
            //      will be rejected until subsequent refreshes as executed successfully.
            markAsInitialized();
        });
    }

    private Optional<Set<String>> getAssociatedThingNames() {
        // use cached value, provided it's not stale
        ThingAssociationV1DTO dto = runtimeConfiguration.getThingAssociationV1();
        // TODO pull from configuration
        if (dto != null && dto.getLastFetched().plusSeconds(DEFAULT_THING_ASSOCIATION_TRUST_DURATION_SECONDS)
                .isBefore(LocalDateTime.now())) {
            logger.atTrace().log("Using locally cached thing associations");
            return Optional.ofNullable(dto.getAssociatedThingNames());
        }

        if (networkStateProvider.getConnectionState() == NetworkStateProvider.ConnectionState.NETWORK_DOWN) {
            logger.atTrace().log("Network down, unable to fetch thing associations from cloud");
            return Optional.empty();
        }

        // otherwise fetch new associations from cloud and write to cache
        logger.atTrace().log("Fetching thing associations from cloud");
        Optional<Set<String>> associatedThingNames = fetchAssociatedThingNames();
        associatedThingNames.ifPresent(names ->
                runtimeConfiguration.putThingAssociationV1(new ThingAssociationV1DTO(names, LocalDateTime.now())));
        return associatedThingNames;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Optional<Set<String>> fetchAssociatedThingNames() {
        try {
            return Optional.of(iotAuthClient.getThingsAssociatedWithCoreDevice()
                    .flatMap(List::stream)
                    .map(AssociatedClientDevice::thingName)
                    .collect(Collectors.toSet()));
        } catch (Exception e) {
            logger.atWarn()
                    .cause(e)
                    .log("Unable to find associated things");
            return Optional.empty();
        }
    }

    private Optional<Map<String, String>> getThingAttributes(String thingName) {
        // use cached value, provided it's not stale
        ThingDescriptionV1DTO dto = runtimeConfiguration.getThingDescriptionV1(thingName);
        // TODO pull from configuration
        if (dto != null && dto.getLastFetched().plusSeconds(DEFAULT_THING_DESCRIPTION_TRUST_DURATION_SECONDS)
                .isBefore(LocalDateTime.now())) {
            logger.atTrace().log("Using locally cached thing description");
            return Optional.ofNullable(dto.getAttributes());
        }

        if (networkStateProvider.getConnectionState() == NetworkStateProvider.ConnectionState.NETWORK_DOWN) {
            logger.atTrace().log("Network down, unable to fetch thing description from cloud");
            return Optional.empty();
        }

        // otherwise fetch new description from cloud and write to cache
        logger.atTrace().log("Fetching thing description from cloud");
        Optional<Map<String, String>> attributes = fetchThingAttributes(thingName);
        attributes.ifPresent(attrs ->
                runtimeConfiguration.putThingDescriptionV1(new ThingDescriptionV1DTO(thingName, attrs, LocalDateTime.now())));
        return attributes;
    }

    private Optional<Map<String, String>> fetchThingAttributes(String thingName) {
        try {
            return Optional.of(iotCoreClient.getThingAttributes(thingName));
        } catch (CloudServiceInteractionException e) {
            logger.atWarn()
                    .cause(e)
                    .kv("thing", thingName)
                    .log("Unable to get thing attributes");
            return Optional.empty();
        }
    }

    public Optional<String> getAttribute(String thingName, String attribute) {
        return Optional.ofNullable(attributesByThing.get(thingName))
                .map(attrs -> attrs.get(attribute));
    }
}
