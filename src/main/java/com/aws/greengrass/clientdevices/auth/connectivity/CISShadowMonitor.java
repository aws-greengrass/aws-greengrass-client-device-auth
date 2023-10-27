/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.mqttclient.MqttClient;
import com.aws.greengrass.mqttclient.WrapperMqttClientConnection;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.RetryUtils;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.iotshadow.IotShadowClient;
import software.amazon.awssdk.iot.iotshadow.model.ErrorResponse;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowRequest;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowResponse;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowSubscriptionRequest;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedEvent;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedSubscriptionRequest;
import software.amazon.awssdk.iot.iotshadow.model.ShadowState;
import software.amazon.awssdk.iot.iotshadow.model.UpdateShadowRequest;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;

@SuppressWarnings("PMD.ImmutableField")
public class CISShadowMonitor implements Consumer<NetworkStateProvider.ConnectionState> {
    private static final Logger LOGGER = LogManager.getLogger(CISShadowMonitor.class);
    private static final String CIS_SHADOW_SUFFIX = "-gci";
    private static final String VERSION = "version";
    static final String SHADOW_UPDATE_DELTA_TOPIC = "$aws/things/%s/shadow/update/delta";
    static final String SHADOW_GET_ACCEPTED_TOPIC = "$aws/things/%s/shadow/get/accepted";

    private static final RetryUtils.RetryConfig SUBSCRIBE_TO_TOPICS_RETRY_CONFIG =
            RetryUtils.RetryConfig.builder()
                    .initialRetryInterval(Duration.ofSeconds(1L))
                    .maxRetryInterval(Duration.ofMinutes(30L))
                    .maxAttempt(Integer.MAX_VALUE)
                    .retryableExceptions(Collections.singletonList(Exception.class))
                    .build();
    private static final RetryUtils.RetryConfig GET_CONNECTIVITY_RETRY_CONFIG =
            RetryUtils.RetryConfig.builder().initialRetryInterval(Duration.ofMinutes(1L))
                    .maxRetryInterval(Duration.ofMinutes(30L)).maxAttempt(Integer.MAX_VALUE)
                    .retryableExceptions(Arrays.asList(ThrottlingException.class, InternalServerException.class))
                    .build();
    private static final RetryUtils.RetryConfig GET_CIS_SHADOW_RETRY_CONFIG =
            RetryUtils.RetryConfig.builder()
                    .initialRetryInterval(Duration.ofSeconds(1L))
                    .maxRetryInterval(Duration.ofMinutes(30L))
                    .maxAttempt(Integer.MAX_VALUE)
                    .retryableExceptions(Collections.singletonList(Exception.class))
                    .build();

    private final Consumer<ShadowDeltaUpdatedEvent> onShadowDeltaUpdated = this::processCISShadow;
    private final Consumer<GetShadowResponse> onGetShadowAccepted = resp -> {
        reportShadowReceived();
        processCISShadow(resp);
    };
    private final Consumer<ErrorResponse> onGetShadowRejected = err -> reportShadowReceived();

    private final Object getShadowLock = new Object();
    private Future<?> getShadowTask;
    AtomicReference<CompletableFuture<?>> shadowGetResponseReceived = new AtomicReference<>();

    private final Supplier<Integer> mqttOperationTimeoutMillis;
    private MqttClientConnection connection;
    private IotShadowClient iotShadowClient;
    private String lastVersion;
    private Future<?> subscribeTaskFuture;
    private final NetworkStateProvider networkStateProvider;
    private final List<CertificateGenerator> monitoredCertificateGenerators = new CopyOnWriteArrayList<>();
    private final ExecutorService executorService;
    private final String shadowName;
    private final ConnectivityInformation connectivityInformation;

    /**
     * Constructor.
     *
     * @param mqttClient              IoT MQTT client
     * @param networkStateProvider    Network State Provider
     * @param executorService         Executor service
     * @param deviceConfiguration     Device configuration
     * @param connectivityInformation Connectivity Info Provider
     */
    @Inject
    public CISShadowMonitor(MqttClient mqttClient,
                            NetworkStateProvider networkStateProvider,
                            ExecutorService executorService,
                            DeviceConfiguration deviceConfiguration,
                            ConnectivityInformation connectivityInformation) {
        this(
                networkStateProvider,
                null,
                null,
                executorService,
                Coerce.toString(deviceConfiguration.getThingName()) + CIS_SHADOW_SUFFIX,
                connectivityInformation,
                mqttClient::getMqttOperationTimeoutMillis
        );
        this.connection = new WrapperMqttClientConnection(mqttClient);
        this.iotShadowClient = new IotShadowClient(this.connection);
    }

    CISShadowMonitor(NetworkStateProvider networkStateProvider,
                     MqttClientConnection connection,
                     IotShadowClient iotShadowClient,
                     ExecutorService executorService,
                     String shadowName,
                     ConnectivityInformation connectivityInformation,
                     Supplier<Integer> mqttOperationTimeoutMillis) {
        this.networkStateProvider = networkStateProvider;
        this.connection = connection;
        this.iotShadowClient = iotShadowClient;
        this.executorService = executorService;
        this.shadowName = shadowName;
        this.connectivityInformation = connectivityInformation;
        this.mqttOperationTimeoutMillis = mqttOperationTimeoutMillis;
    }

    /**
     * Start shadow monitor.
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void startMonitor() {
        if (subscribeTaskFuture != null) {
            subscribeTaskFuture.cancel(true);
        }
        subscribeTaskFuture = executorService.submit(() -> {
            try {
                RetryUtils.runWithRetry(
                        SUBSCRIBE_TO_TOPICS_RETRY_CONFIG,
                        () -> {
                            subscribeToShadowTopics();
                            return null;
                        },
                        "subscribe-to-cis-topics",
                        LOGGER);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.atError().cause(e).log("Unexpected failure when subscribing to shadow topics");
                return;
            }

            try {
                fetchCISShadowWithRetriesAsync();
            } catch (Exception e) {
                LOGGER.atError().cause(e).log("Unable to fetch shadow on monitor startup");
            }
        });
    }

    /**
     * Stop shadow monitor.
     */
    public void stopMonitor() {
        if (subscribeTaskFuture != null) {
            subscribeTaskFuture.cancel(true);
        }
        unsubscribeFromShadowTopics();
    }

    /**
     * Add cert to CIS shadow monitor.
     *
     * @param certificateGenerator CertificateGenerator instance for the certificate
     */
    public void addToMonitor(CertificateGenerator certificateGenerator) {
        monitoredCertificateGenerators.add(certificateGenerator);
    }

    /**
     * Remove cert from CIS shadow monitor.
     *
     * @param certificateGenerator CertificateGenerator instance for the certificate
     */
    public void removeFromMonitor(CertificateGenerator certificateGenerator) {
        monitoredCertificateGenerators.remove(certificateGenerator);
    }

    private void subscribeToShadowTopics() throws InterruptedException, ExecutionException {
        ShadowDeltaUpdatedSubscriptionRequest shadowDeltaUpdatedSubscriptionRequest =
                new ShadowDeltaUpdatedSubscriptionRequest();
        shadowDeltaUpdatedSubscriptionRequest.thingName = shadowName;
        iotShadowClient.SubscribeToShadowDeltaUpdatedEvents(
                        shadowDeltaUpdatedSubscriptionRequest,
                        QualityOfService.AT_LEAST_ONCE,
                        onShadowDeltaUpdated,
                        (e) -> LOGGER.atError()
                                .log("Error processing shadowDeltaUpdatedSubscription Response", e))
                .get();
        LOGGER.atInfo().log("Subscribed to shadow update delta topic");

        GetShadowSubscriptionRequest getShadowSubscriptionRequest = new GetShadowSubscriptionRequest();
        getShadowSubscriptionRequest.thingName = shadowName;
        iotShadowClient.SubscribeToGetShadowAccepted(
                        getShadowSubscriptionRequest,
                        QualityOfService.AT_LEAST_ONCE,
                        onGetShadowAccepted,
                        (e) -> LOGGER.atError().log("Error processing getShadowSubscription Response", e))
                .get();
        LOGGER.atInfo().log("Subscribed to shadow get accepted topic");

        GetShadowSubscriptionRequest getShadowRejectedSubscriptionRequest = new GetShadowSubscriptionRequest();
        getShadowRejectedSubscriptionRequest.thingName = shadowName;
        iotShadowClient.SubscribeToGetShadowRejected(
                        getShadowRejectedSubscriptionRequest,
                        QualityOfService.AT_LEAST_ONCE,
                        onGetShadowRejected,
                        (e) -> LOGGER.atError().log("Error processing get shadow rejected response", e))
                .get();
        LOGGER.atInfo().log("Subscribed to shadow get rejected topic");
    }

    private void processCISShadow(GetShadowResponse response) {
        String cisVersion = Coerce.toString(response.state.desired.get("version"));
        processCISShadow(cisVersion, response.state.desired);
    }

    private void processCISShadow(ShadowDeltaUpdatedEvent event) {
        String cisVersion = Coerce.toString(event.state.get("version"));
        processCISShadow(cisVersion, event.state);
    }

    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.PrematureDeclaration"})
    private synchronized void processCISShadow(String version, Map<String, Object> desiredState) {
        if (version == null) {
            LOGGER.atWarn().log("Ignoring CIS shadow response, version is missing");
            return;
        }

        if (Objects.equals(version, lastVersion)) {
            LOGGER.atInfo().kv(VERSION, version).log("Already processed version. Skipping cert re-generation");
            updateCISShadowReportedState(desiredState);
            return;
        }

        LOGGER.atInfo().log("New CIS version: {}", version);

        // NOTE: This method executes in an MQTT CRT thread. Since certificate generation is a compute intensive
        // operation (particularly on low end devices) it is imperative that we process this event asynchronously
        // to avoid blocking other MQTT subscribers in the Nucleus
        CompletableFuture.runAsync(() -> {

            Set<String> prevCachedHostAddresses = new HashSet<>(connectivityInformation.getCachedHostAddresses());

            Optional<List<ConnectivityInfo>> connectivityInfo;
            try {
                connectivityInfo = RetryUtils.runWithRetry(
                        GET_CONNECTIVITY_RETRY_CONFIG,
                        connectivityInformation::getConnectivityInfo,
                        "get-connectivity",
                        LOGGER
                );
            } catch (InterruptedException e) {
                LOGGER.atDebug().kv(VERSION, version).cause(e)
                        .log("Retry workflow for getting connectivity info interrupted");
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.atError().kv(VERSION, version).cause(e)
                        .log("Failed to get connectivity info from cloud. Check that the core device's IoT policy "
                                + "grants the greengrass:GetConnectivityInfo permission");
                return;
            }

            if (!connectivityInfo.isPresent()) {
                // CIS call failed for either ValidationException or ResourceNotFoundException.
                // We won't retry in this case, but we will update the CIS shadow reported state
                // to signal that we have fully processed this version.
                try {
                    LOGGER.atInfo().kv(VERSION, version)
                            .log("No connectivity info found. Skipping cert re-generation");
                    updateCISShadowReportedState(desiredState);
                } finally {
                    // Don't process the same version again
                    lastVersion = version;
                }
                return;
            }

            // skip cert rotation if connectivity info hasn't changed
            Set<String> cachedHostAddresses = new HashSet<>(connectivityInformation.getCachedHostAddresses());
            if (Objects.equals(prevCachedHostAddresses, cachedHostAddresses)) {
                try {
                    LOGGER.atInfo().kv(VERSION, version)
                            .log("Connectivity info hasn't changed. Skipping cert re-generation");
                    // update the CIS shadow reported state
                    // to signal that we have fully processed this version.
                    updateCISShadowReportedState(desiredState);
                } finally {
                    // Don't process the same version again
                    lastVersion = version;
                }
                return;
            }

            try {
                for (CertificateGenerator cg : monitoredCertificateGenerators) {
                    cg.generateCertificate(() -> new ArrayList<>(cachedHostAddresses), "connectivity info was updated");
                }
                LOGGER.atDebug().log("certificates rotated");
            } catch (CertificateGenerationException e) {
                LOGGER.atError().kv(VERSION, version).cause(e).log("Failed to generate new certificates");
                return;
            }

            try {
                // update CIS shadow so reported state matches desired state
                updateCISShadowReportedState(desiredState);
            } finally {
                // Don't process the same version again
                lastVersion = version;
            }
        }, executorService).exceptionally(e -> {
            LOGGER.atError().kv(VERSION, version).cause(e).log("Unable to handle CIS shadow delta");
            return null;
        });
    }

    /**
     * Asynchronously update the CIS shadow's reported state for the given shadow version.
     *
     * @param reportedState CIS shadow reported state
     */
    private void updateCISShadowReportedState(Map<String, Object> reportedState) {
        UpdateShadowRequest updateShadowRequest = new UpdateShadowRequest();
        updateShadowRequest.thingName = shadowName;
        updateShadowRequest.state = new ShadowState();
        updateShadowRequest.state.reported = reportedState == null ? null : new HashMap<>(reportedState);
        iotShadowClient.PublishUpdateShadow(updateShadowRequest, QualityOfService.AT_LEAST_ONCE).exceptionally(e -> {
            LOGGER.atWarn().cause(e).log("Unable to update CIS shadow reported state");
            return null;
        });
    }

    private void reportShadowReceived() {
        CompletableFuture<?> shadowReceived = this.shadowGetResponseReceived.get();
        if (shadowReceived != null) {
            shadowReceived.complete(null);
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void fetchCISShadowWithRetriesAsync() {
        if (networkStateProvider.getConnectionState() == NetworkStateProvider.ConnectionState.NETWORK_DOWN) {
            // will be retried when online again
            return;
        }
        synchronized (getShadowLock) {
            if (getShadowTask != null && !getShadowTask.isDone()) {
                // operation already in progress
                return;
            }
            getShadowTask = executorService.submit(() -> {
                try {
                    RetryUtils.runWithRetry(
                            GET_CIS_SHADOW_RETRY_CONFIG,
                            () -> {
                                CompletableFuture<?> shadowGetResponseReceived = new CompletableFuture<>();
                                this.shadowGetResponseReceived.set(shadowGetResponseReceived);
                                publishToGetCISShadowTopic().get();
                                // await shadow get accepted, rejected
                                long waitForGetResponseTimeout =
                                        Duration.ofMillis(mqttOperationTimeoutMillis.get())
                                                .plusSeconds(5L) // buffer
                                                .toMillis();
                                shadowGetResponseReceived.get(waitForGetResponseTimeout, TimeUnit.MILLISECONDS);
                                return null;
                            },
                            "get-cis-shadow",
                            LOGGER);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    LOGGER.atError().cause(e).log("unable to get CIS shadow");
                }
            });
        }
    }

    private void cancelFetchCISShadow() {
        synchronized (getShadowLock) {
            if (getShadowTask != null) {
                getShadowTask.cancel(true);
            }
        }
    }

    private CompletableFuture<Integer> publishToGetCISShadowTopic() {
        LOGGER.atDebug().log("Publishing to get shadow topic");
        GetShadowRequest getShadowRequest = new GetShadowRequest();
        getShadowRequest.thingName = shadowName;
        return iotShadowClient.PublishGetShadow(getShadowRequest, QualityOfService.AT_MOST_ONCE)
                .exceptionally(e -> {
                    LOGGER.atWarn().cause(e).log("Unable to retrieve CIS shadow");
                    return null;
                });
    }

    private void unsubscribeFromShadowTopics() {
        if (connection != null) {
            LOGGER.atDebug().log("Unsubscribing from CIS shadow topics");
            String topic = String.format(SHADOW_UPDATE_DELTA_TOPIC, shadowName);
            connection.unsubscribe(topic);

            topic = String.format(SHADOW_GET_ACCEPTED_TOPIC, shadowName);
            connection.unsubscribe(topic);
        }
    }

    @Override
    public void accept(NetworkStateProvider.ConnectionState state) {
        if (state == NetworkStateProvider.ConnectionState.NETWORK_UP) {
            fetchCISShadowWithRetriesAsync();
        } else if (state == NetworkStateProvider.ConnectionState.NETWORK_DOWN) {
            cancelFetchCISShadow();
        }
    }
}
