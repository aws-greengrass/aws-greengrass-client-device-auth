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
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
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
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;

@SuppressWarnings("PMD.ImmutableField")
public class CISShadowMonitor {
    private static final Logger LOGGER = LogManager.getLogger(CISShadowMonitor.class);
    private static final String CIS_SHADOW_SUFFIX = "-gci";
    private static final String VERSION = "version";
    private static final long TIMEOUT_FOR_SUBSCRIBING_TO_TOPICS_SECONDS = Duration.ofMinutes(1).getSeconds();
    private static final long WAIT_TIME_TO_SUBSCRIBE_AGAIN_IN_MS = Duration.ofMinutes(2).toMillis();
    private static final Random JITTER = new Random();
    private static final String SHADOW_UPDATE_DELTA_TOPIC = "$aws/things/%s/shadow/update/delta";
    private static final String SHADOW_GET_ACCEPTED_TOPIC = "$aws/things/%s/shadow/get/accepted";

    private static final RetryUtils.RetryConfig GET_CONNECTIVITY_RETRY_CONFIG = RetryUtils.RetryConfig.builder()
            .initialRetryInterval(Duration.ofMinutes(1L)).maxRetryInterval(Duration.ofMinutes(30L))
            .maxAttempt(Integer.MAX_VALUE).retryableExceptions(Arrays.asList(ThrottlingException.class,
                    InternalServerException.class)).build();

    /**
     * Delay between consecutive attempts to process CIS shadow state.
     * Must be greater than zero.
     */
    @Setter(AccessLevel.PACKAGE) // for unit testing
    private long shadowProcessingDelayMs = 5000L;

    /**
     * Handler that executes when {@link ShadowProcessor} has finished
     * all of its queued work.
     */
    @Setter(AccessLevel.PACKAGE) // for unit testing
    private volatile Runnable onShadowProcessingWorkComplete;

    private MqttClientConnection connection;
    private IotShadowClient iotShadowClient;
    private Future<?> subscribeTaskFuture;
    private final List<CertificateGenerator> monitoredCertificateGenerators = new CopyOnWriteArrayList<>();
    private final ExecutorService executorService;
    private final String shadowName;
    private final ConnectivityInfoProvider connectivityInfoProvider;
    private final ShadowProcessor shadowProcessor;

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
     * Construct a new CISShadowMonitor.
     *
     * @param mqttClient               IoT MQTT client
     * @param ses                      Scheduled executor service
     * @param executorService          Executor service
     * @param deviceConfiguration      Device configuration
     * @param connectivityInfoProvider Connectivity Info Provider
     */
    @Inject
    public CISShadowMonitor(MqttClient mqttClient,
                            ScheduledExecutorService ses,
                            ExecutorService executorService,
                            DeviceConfiguration deviceConfiguration,
                            ConnectivityInfoProvider connectivityInfoProvider) {
        this(mqttClient, null, null, ses, executorService,
                Coerce.toString(deviceConfiguration.getThingName()) + CIS_SHADOW_SUFFIX, connectivityInfoProvider);
        this.connection = new WrapperMqttClientConnection(mqttClient);
        this.iotShadowClient = new IotShadowClient(this.connection);
    }

    CISShadowMonitor(MqttClient mqttClient,
                     MqttClientConnection connection,
                     IotShadowClient iotShadowClient,
                     ScheduledExecutorService ses,
                     ExecutorService executorService,
                     String shadowName,
                     ConnectivityInfoProvider connectivityInfoProvider) {
        mqttClient.addToCallbackEvents(callbacks);
        this.connection = connection;
        this.iotShadowClient = iotShadowClient;
        this.executorService = executorService;
        this.shadowName = shadowName;
        this.connectivityInfoProvider = connectivityInfoProvider;
        this.shadowProcessor = new ShadowProcessor(ses);
    }

    /**
     * Start shadow monitor.
     */
    public void startMonitor() {
        shadowProcessor.start();

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

        shadowProcessor.stop();
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

    private void publishToGetCISShadowTopic() {
        LOGGER.info("Publishing to get shadow topic");
        GetShadowRequest getShadowRequest = new GetShadowRequest();
        getShadowRequest.thingName = shadowName;
        iotShadowClient.PublishGetShadow(getShadowRequest, QualityOfService.AT_LEAST_ONCE);
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
                                this::queueCISShadowProcessing,
                                (e) -> LOGGER.atError()
                                        .log("Error processing shadowDeltaUpdatedSubscription Response", e))
                        .get(TIMEOUT_FOR_SUBSCRIBING_TO_TOPICS_SECONDS, TimeUnit.SECONDS);
                LOGGER.info("Subscribed to shadow update delta topic");

                GetShadowSubscriptionRequest getShadowSubscriptionRequest = new GetShadowSubscriptionRequest();
                getShadowSubscriptionRequest.thingName = shadowName;
                iotShadowClient.SubscribeToGetShadowAccepted(getShadowSubscriptionRequest,
                                QualityOfService.AT_LEAST_ONCE,
                                this::queueCISShadowProcessing,
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

    private void unsubscribeFromShadowTopics() {
        if (connection != null) {
            LOGGER.atDebug().log("Unsubscribing from CIS shadow topics");
            String topic = String.format(SHADOW_UPDATE_DELTA_TOPIC, shadowName);
            connection.unsubscribe(topic);

            topic = String.format(SHADOW_GET_ACCEPTED_TOPIC, shadowName);
            connection.unsubscribe(topic);
        }
    }

    private void queueCISShadowProcessing(GetShadowResponse response) {
        shadowProcessor.queueProcessingRequest(
                ShadowProcessingRequest.builder()
                        .version(response.version)
                        .desiredState(response.state.desired)
                        .build());
    }

    private void queueCISShadowProcessing(ShadowDeltaUpdatedEvent event) {
        shadowProcessor.queueProcessingRequest(
                ShadowProcessingRequest.builder()
                        .version(event.version)
                        .desiredState(event.state)
                        .build());
    }

    @Data
    @Builder
    private static class ShadowProcessingRequest {
        private final int version;
        private final Map<String, Object> desiredState;
    }

    /**
     * Responsible for processing CIS shadow state.
     *
     * <p>The goal of this class is to react to changes in CIS connectivity
     * and rotate registered certificates using the new host information.
     *
     * <p>Shadow processing by this class is idempotent, in the sense that duplicate requests
     * to process the same shadow version will not result in unnecessary certificate rotations.
     * Certificate rotation will only occur if this class detects changes in connectivity info
     * from CIS.
     *
     * <p>If certificate rotation is successful, CIS shadow reported state
     * will be updated to match desired state.
     */
    @RequiredArgsConstructor
    private class ShadowProcessor {

        /**
         * Request from {@link CISShadowMonitor} to process shadow state.
         */
        private final AtomicReference<ShadowProcessingRequest> shadowProcessingRequest = new AtomicReference<>();

        /**
         * Last received CIS shadow version, used to de-duplicate processing requests.
         */
        private final AtomicInteger lastCISShadowVersion = new AtomicInteger();

        /**
         * Host addresses obtained from CIS, used to de-duplicate processing requests.
         */
        private final AtomicReference<List<String>> hostAddresses = new AtomicReference<>();

        private final ScheduledExecutorService ses;

        /**
         * Scheduled task for handling shadow processing requests.
         */
        private Future<?> shadowProcessingTask;

        /**
         * Start processing shadow requests.
         */
        void start() {
            if (shadowProcessingTask != null) {
                shadowProcessingTask.cancel(true);
            }
            shadowProcessingTask = ses.scheduleWithFixedDelay(
                    this::handleShadowProcessingRequest,
                    0L,
                    shadowProcessingDelayMs,
                    TimeUnit.MILLISECONDS
            );
        }

        /**
         * Stop processing shadow requests and interrupt if one is in-progress.
         */
        void stop() {
            if (shadowProcessingTask != null) {
                shadowProcessingTask.cancel(true);
            }
        }

        /**
         * Enqueue a request to process shadow state. This method is non-blocking.
         *
         * @param request shadow processing request
         */
        void queueProcessingRequest(ShadowProcessingRequest request) {
            // We are only able to update the latest shadow version,
            // so we can safely ignore old shadow versions, since we don't
            // use the actual contents of shadow state.
            // https://docs.aws.amazon.com/iot/latest/developerguide/device-shadow-data-flow.html#optimistic-locking
            shadowProcessingRequest.updateAndGet(currRequest -> {
                if (currRequest == null) {
                    return request;
                }

                // new request was received
                if (request.getVersion() > currRequest.getVersion()) {
                    return request;
                }

                return currRequest;
            });
        }

        private void dequeueProcessingRequest(ShadowProcessingRequest request) {
            shadowProcessingRequest.getAndUpdate(currRequest -> {

                // a newer request was received,
                // keep it for next processing interval
                if (currRequest.getVersion() > request.getVersion()) {
                    return currRequest;
                }

                notifyWorkComplete();
                return null;
            });
        }

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private void handleShadowProcessingRequest() {
            ShadowProcessingRequest request = shadowProcessingRequest.get();
            if (request == null) {
                return;
            }
            try {
                handleShadowProcessingRequest(request);
            } catch (Exception e) {
                LOGGER.atError().kv(VERSION, request.getVersion()).cause(e)
                        .log("Unable to process CIS shadow");
            } finally {
                dequeueProcessingRequest(request);
            }
        }

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private void handleShadowProcessingRequest(ShadowProcessingRequest request) {
            int version = request.getVersion();
            Map<String, Object> desiredState = request.getDesiredState();

            if (version == lastCISShadowVersion.get()) {
                LOGGER.atInfo().kv(VERSION, version)
                        .log("Already processed CIS shadow version. Skipping cert regeneration");
                updateCISShadowReportedState(version, desiredState);
                return;
            }

            LOGGER.atInfo().log("New CIS version: {}", version);

            List<String> newHostAddresses;
            try {
                newHostAddresses = fetchHostAddressesFromCIS();
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
            }

            List<String> previousHostAddresses = hostAddresses.getAndSet(newHostAddresses);
            if (Objects.equals(previousHostAddresses, newHostAddresses)) {
                LOGGER.atInfo().kv(VERSION, version)
                        .log("No change in connectivity info. Skipping cert regeneration");
                updateCISShadowReportedState(version, desiredState);
                return;
            }

            try {
                for (CertificateGenerator cg : monitoredCertificateGenerators) {
                    cg.generateCertificate(() -> newHostAddresses, "connectivity info was updated");
                }
            } catch (KeyStoreException e) {
                LOGGER.atError().kv(VERSION, version).cause(e).log("Failed to generate new certificates");
                return;
            }

            try {
                updateCISShadowReportedState(version, desiredState);
            } finally {
                lastCISShadowVersion.set(version);
            }
        }

        @SuppressWarnings("PMD.SignatureDeclareThrowsException")
        private List<String> fetchHostAddressesFromCIS() throws Exception {
            RetryUtils.runWithRetry(
                    GET_CONNECTIVITY_RETRY_CONFIG,
                    connectivityInfoProvider::getConnectivityInfo,
                    "get-connectivity",
                    LOGGER
            );
            return connectivityInfoProvider.getCachedHostAddresses();
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

        /**
         * Signal that current work has been completed.
         * Intended for unit testing.
         */
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private void notifyWorkComplete() {
            if (onShadowProcessingWorkComplete != null) {
                try {
                    onShadowProcessingWorkComplete.run();
                } catch (Exception e) {
                    LOGGER.atDebug().cause(e).log("Unable to notify work complete");
                }
            }
        }
    }
}
