/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.cisclient.ConnectivityInfoProvider;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.mqttclient.MqttClient;
import com.aws.greengrass.mqttclient.WrapperMqttClientConnection;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.RetryUtils;
import lombok.AccessLevel;
import lombok.Getter;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;
import software.amazon.awssdk.crt.mqtt.MqttException;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.iotshadow.IotShadowClient;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowRequest;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowResponse;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowSubscriptionRequest;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedEvent;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedSubscriptionRequest;
import software.amazon.awssdk.iot.iotshadow.model.ShadowState;
import software.amazon.awssdk.iot.iotshadow.model.UpdateShadowRequest;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;

import java.security.KeyStoreException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;

@SuppressWarnings("PMD.ImmutableField")
public class CISShadowMonitor {
    private static final Logger LOGGER = LogManager.getLogger(CISShadowMonitor.class);
    private static final String CIS_SHADOW_SUFFIX = "-gci";
    private static final String VERSION = "version";
    private static final long TIMEOUT_FOR_SUBSCRIBING_TO_TOPICS_SECONDS = Duration.ofMinutes(1).getSeconds();
    private static final long WAIT_TIME_TO_SUBSCRIBE_AGAIN_IN_MS = Duration.ofMinutes(2).toMillis();
    private static final Random JITTER = new Random();
    static final String SHADOW_UPDATE_DELTA_TOPIC = "$aws/things/%s/shadow/update/delta";
    static final String SHADOW_GET_ACCEPTED_TOPIC = "$aws/things/%s/shadow/get/accepted";

    private static final RetryUtils.RetryConfig GET_CONNECTIVITY_RETRY_CONFIG = RetryUtils.RetryConfig.builder()
            .initialRetryInterval(Duration.ofMinutes(1L)).maxRetryInterval(Duration.ofMinutes(30L))
            .maxAttempt(Integer.MAX_VALUE).retryableExceptions(Arrays.asList(ThrottlingException.class,
                    InternalServerException.class)).build();

    private MqttClientConnection connection;
    private IotShadowClient iotShadowClient;
    private int lastVersion = 0;
    private Future<?> subscribeTaskFuture;
    private final AtomicBoolean connectivityCallInProgress = new AtomicBoolean();
    private final List<CertificateGenerator> monitoredCertificateGenerators = new CopyOnWriteArrayList<>();
    private final ExecutorService executorService;
    private final String shadowName;
    private final ConnectivityInfoProvider connectivityInfoProvider;

    @Getter(AccessLevel.PACKAGE) // for unit tests
    private final MqttClientConnectionEvents callbacks = new MqttClientConnectionEvents() {
        @Override
        public void onConnectionInterrupted(int errorCode) {
        }

        @Override
        public void onConnectionResumed(boolean sessionPresent) {
            executorService.execute(() -> {
                // Get the shadow state when connection is re-established by publishing to get topic
                publishToGetCISShadowTopic();
            });
        }
    };

    /**
     * Constructor.
     *
     * @param mqttClient               IoT MQTT client
     * @param executorService          Executor service
     * @param deviceConfiguration      Device configuration
     * @param connectivityInfoProvider Connectivity Info Provider
     */
    @Inject
    public CISShadowMonitor(MqttClient mqttClient, ExecutorService executorService,
                            DeviceConfiguration deviceConfiguration,
                            ConnectivityInfoProvider connectivityInfoProvider) {
        this(mqttClient, null, null, executorService,
                Coerce.toString(deviceConfiguration.getThingName()) + CIS_SHADOW_SUFFIX, connectivityInfoProvider);
        this.connection = new WrapperMqttClientConnection(mqttClient);
        this.iotShadowClient = new IotShadowClient(this.connection);
    }

    CISShadowMonitor(MqttClient mqttClient, MqttClientConnection connection, IotShadowClient iotShadowClient,
                     ExecutorService executorService, String shadowName,
                     ConnectivityInfoProvider connectivityInfoProvider) {
        mqttClient.addToCallbackEvents(callbacks);
        this.connection = connection;
        this.iotShadowClient = iotShadowClient;
        this.executorService = executorService;
        this.shadowName = shadowName;
        this.connectivityInfoProvider = connectivityInfoProvider;
    }

    /**
     * Start shadow monitor.
     */
    public void startMonitor() {
        if (subscribeTaskFuture != null) {
            subscribeTaskFuture.cancel(true);
        }
        subscribeTaskFuture = executorService.submit(() -> {
            try {
                subscribeToShadowTopics();
                publishToGetCISShadowTopic();
            } catch (InterruptedException e) {
                LOGGER.atWarn().cause(e).log("Interrupted while subscribing to CIS shadow topics");
                Thread.currentThread().interrupt();
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

    private void subscribeToShadowTopics() throws InterruptedException {
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while subscribing to CIS shadow topics");
            }
            try {
                ShadowDeltaUpdatedSubscriptionRequest shadowDeltaUpdatedSubscriptionRequest
                        = new ShadowDeltaUpdatedSubscriptionRequest();
                shadowDeltaUpdatedSubscriptionRequest.thingName = shadowName;
                iotShadowClient.SubscribeToShadowDeltaUpdatedEvents(shadowDeltaUpdatedSubscriptionRequest,
                                QualityOfService.AT_LEAST_ONCE,
                                this::processCISShadow,
                                (e) -> LOGGER.atError()
                                        .log("Error processing shadowDeltaUpdatedSubscription Response", e))
                        .get(TIMEOUT_FOR_SUBSCRIBING_TO_TOPICS_SECONDS, TimeUnit.SECONDS);
                LOGGER.info("Subscribed to shadow update delta topic");

                GetShadowSubscriptionRequest getShadowSubscriptionRequest = new GetShadowSubscriptionRequest();
                getShadowSubscriptionRequest.thingName = shadowName;
                iotShadowClient.SubscribeToGetShadowAccepted(getShadowSubscriptionRequest,
                                QualityOfService.AT_LEAST_ONCE,
                                this::processCISShadow,
                                (e) -> LOGGER.atError()
                                        .log("Error processing getShadowSubscription Response", e))
                        .get(TIMEOUT_FOR_SUBSCRIBING_TO_TOPICS_SECONDS, TimeUnit.SECONDS);
                LOGGER.info("Subscribed to shadow get accepted topic");
                return;

            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof MqttException || cause instanceof TimeoutException) {
                    LOGGER.atWarn().setCause(cause).log("Caught exception while subscribing to shadow topics, "
                            + "will retry shortly");
                } else if (cause instanceof InterruptedException) {
                    throw (InterruptedException) cause;
                } else {
                    LOGGER.atError().setCause(e)
                            .log("Caught exception while subscribing to shadow topics, will retry shortly");
                }
            } catch (TimeoutException e) {
                LOGGER.atWarn().setCause(e).log("Subscribe to shadow topics timed out, will retry shortly");
            }
            // Wait for sometime and then try to subscribe again
            Thread.sleep(WAIT_TIME_TO_SUBSCRIBE_AGAIN_IN_MS + JITTER.nextInt(10_000));
        }
    }

    private void processCISShadow(GetShadowResponse response) {
        processCISShadow(response.version, response.state.desired);
    }

    private void processCISShadow(ShadowDeltaUpdatedEvent event) {
        processCISShadow(event.version, event.state);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private synchronized void processCISShadow(int version, Map<String, Object> desiredState) {
        if (version == lastVersion) {
            LOGGER.atInfo().kv(VERSION, version).log("Already processed version. Skipping cert re-generation");
            updateCISShadowReportedState(version, desiredState);
            return;
        }

        LOGGER.atInfo().log("New CIS version: {}", version);

        // NOTE: This method executes in an MQTT CRT thread. Since certificate generation is a compute intensive
        // operation (particularly on low end devices) it is imperative that we process this event asynchronously
        // to avoid blocking other MQTT subscribers in the Nucleus
        CompletableFuture.runAsync(() -> {
            if (connectivityCallInProgress.getAndSet(true)) {
                LOGGER.atDebug().log("getConnectivityInfo call in progress. Skipping cert re-generation");
                return;
            }
            try {
                RetryUtils.runWithRetry(GET_CONNECTIVITY_RETRY_CONFIG, connectivityInfoProvider::getConnectivityInfo,
                        "get-connectivity", LOGGER);
            } catch (InterruptedException e) {
                LOGGER.atWarn().kv(VERSION, version).cause(e)
                        .log("Retry workflow for getting connectivity info interrupted");
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOGGER.atError().kv(VERSION, version).cause(e)
                        .log("Failed to get connectivity info from cloud."
                                + " Check that the core device's IoT policy "
                                + "grants the greengrass:GetConnectivityInfo permission");
                return;
            } finally {
                connectivityCallInProgress.set(false);
            }

            try {
                for (CertificateGenerator cg : monitoredCertificateGenerators) {
                    cg.generateCertificate(connectivityInfoProvider::getCachedHostAddresses,
                            "connectivity info was updated");
                }
            } catch (KeyStoreException e) {
                LOGGER.atError().kv(VERSION, version).cause(e).log("Failed to generate new certificates");
                return;
            }

            try {
                updateCISShadowReportedState(version, desiredState);
            } finally {
                lastVersion = version;
            }
        }, executorService).exceptionally(e -> {
            LOGGER.atError().kv(VERSION, version).cause(e).log("Unable to handle CIS shadow delta");
            return null;
        });
    }

    /**
     * Asynchronously update the CIS shadow's <b>reported</b> state for the given shadow version.
     *
     * @param version CIS shadow version
     * @param desired CIS shadow <b>desired</b> state
     */
    private void updateCISShadowReportedState(int version, Map<String, Object> desired) {
        LOGGER.atInfo().kv(VERSION, version).log("Reporting CIS version");
        UpdateShadowRequest updateShadowRequest = new UpdateShadowRequest();
        updateShadowRequest.thingName = shadowName;
        updateShadowRequest.version = version;
        updateShadowRequest.state = new ShadowState();
        updateShadowRequest.state.reported = desired == null ? null : new HashMap<>(desired);
        iotShadowClient.PublishUpdateShadow(updateShadowRequest, QualityOfService.AT_LEAST_ONCE)
                .exceptionally(e -> {
                    LOGGER.atWarn().kv(VERSION, version).cause(e).log("Unable to report CIS shadow version");
                    return null;
                });
    }

    private void publishToGetCISShadowTopic() {
        LOGGER.info("Publishing to get shadow topic");
        GetShadowRequest getShadowRequest = new GetShadowRequest();
        getShadowRequest.thingName = shadowName;
        iotShadowClient.PublishGetShadow(getShadowRequest, QualityOfService.AT_LEAST_ONCE);
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
}
