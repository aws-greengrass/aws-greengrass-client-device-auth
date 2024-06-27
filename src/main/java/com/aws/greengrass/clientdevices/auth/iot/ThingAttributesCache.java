/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import software.amazon.awssdk.services.greengrassv2.model.AssociatedClientDevice;

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

    // set once during component install
    private static final AtomicReference<ThingAttributesCache> INSTANCE = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> initialized = new AtomicReference<>();

    private final IotCoreClient iotCoreClient;
    private final IotAuthClient iotAuthClient;

    private final Map<String, Map<String, String>> attributesByThing = new ConcurrentHashMap<>();

    private final ScheduledExecutorService ses;
    private final NetworkStateProvider networkStateProvider;
    private ScheduledFuture<?> refreshTask;

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
     * @param ses                  scheduled executor service
     */
    @Inject
    public ThingAttributesCache(IotCoreClient iotCoreClient,
                                IotAuthClient iotAuthClient,
                                NetworkStateProvider networkStateProvider,
                                ScheduledExecutorService ses) {
        this.iotCoreClient = iotCoreClient;
        this.iotAuthClient = iotAuthClient;
        this.networkStateProvider = networkStateProvider;
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
        // TODO configurable delay
        refreshTask = ses.scheduleWithFixedDelay(this::refresh, 0L, 1L, TimeUnit.MINUTES);
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
        if (networkStateProvider.getConnectionState() == NetworkStateProvider.ConnectionState.NETWORK_DOWN) {
            // TODO cache attributes on disk and load here, handle case if device restarts while offline
            logger.atTrace().log("network down, unable to refresh thing-attribute cache");
            return;
        }
        logger.atTrace().log("beginning thing-attribute cache refresh");
        getAssociatedThingNames().ifPresent(thingNames -> {
            for (String thingName : thingNames) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                fetchDeviceAttributes(thingName).ifPresent(attrs -> {
                    logger.atInfo().kv("thing", thingName).log("attributes refreshed for device");
                    attributesByThing.put(thingName, new ConcurrentHashMap<>(attrs));
                });
            }
            // TODO handle case where some fetches fail
            markAsInitialized();
        });
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Optional<Set<String>> getAssociatedThingNames() {
        try {
            return Optional.of(iotAuthClient.getThingsAssociatedWithCoreDevice()
                    .flatMap(List::stream)
                    .map(AssociatedClientDevice::thingName)
                    .collect(Collectors.toSet()));
        } catch (Exception e) {
            logger.atWarn()
                    .log("Unable to find associated things");
            return Optional.empty();
        }
    }

    private Optional<Map<String, String>> fetchDeviceAttributes(String thingName) {
        try {
            return Optional.ofNullable(iotCoreClient.getThingAttributes(thingName));
        } catch (CloudServiceInteractionException e) {
            logger.atWarn()
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
