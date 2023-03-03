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
import lombok.Getter;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
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
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import javax.inject.Inject;

@SuppressWarnings("PMD.ImmutableField")
public class CISShadowMonitor implements Consumer<NetworkStateProvider.ConnectionState> {
    private static final Logger LOGGER = LogManager.getLogger(CISShadowMonitor.class);
    private static final String CIS_SHADOW_SUFFIX = "-gci";
    private static final long TIMEOUT_FOR_SUBSCRIBING_TO_TOPICS_SECONDS = Duration.ofMinutes(1).getSeconds();
    private static final long WAIT_TIME_TO_SUBSCRIBE_AGAIN_IN_MS = Duration.ofMinutes(2).toMillis();
    private static final Random JITTER = new Random();
    static final String SHADOW_UPDATE_DELTA_TOPIC = "$aws/things/%s/shadow/update/delta";
    static final String SHADOW_GET_ACCEPTED_TOPIC = "$aws/things/%s/shadow/get/accepted";

    private static final RetryUtils.RetryConfig GET_CONNECTIVITY_RETRY_CONFIG =
            RetryUtils.RetryConfig.builder()
                    .initialRetryInterval(Duration.ofMinutes(1L))
                    .maxRetryInterval(Duration.ofMinutes(30L))
                    // not retrying forever, to allow the overall CIS shadow processing task
                    // to be retried, as we may have received a newer version while waiting
                    // for this call to succeed.
                    .maxAttempt(5)
                    .retryableExceptions(Arrays.asList(ThrottlingException.class, InternalServerException.class))
                    .build();

    private MqttClientConnection connection;
    private IotShadowClient iotShadowClient;
    private Future<?> subscribeTaskFuture;
    /**
     * Task to process a CIS shadow.
     */
    private Future<?> shadowTask;
    /**
     * Protects access to {@link CISShadowMonitor#shadowTask}.
     */
    private final Object shadowTaskLock = new Object();
    /**
     * Queue for incoming requests to process the CIS shadow.
     * We receive requests whenever CIS shadow has a delta update, or
     * when we request a shadow (get shadow) during startup or when we
     * regain network connectivity.  If processing of a task fails,
     * it's put back on the queue to retry.
     */
    private final CISShadowProcessingQueue queue = new CISShadowProcessingQueue();
    /**
     * Task that pulls a {@link ProcessCISShadowTask} from the queue
     * and executes it, if one exists.
     */
    private Future<?> processShadowTasks;
    private final List<CertificateGenerator> monitoredCertificateGenerators = new CopyOnWriteArrayList<>();
    private final ExecutorService executorService;
    private final String shadowName;
    private final ConnectivityInformation connectivityInformation;


    /**
     * Constructor.
     *
     * @param mqttClient              IoT MQTT client
     * @param executorService         Executor service
     * @param deviceConfiguration     Device configuration
     * @param connectivityInformation Connectivity Info Provider
     */
    @Inject
    public CISShadowMonitor(MqttClient mqttClient, ExecutorService executorService,
                            DeviceConfiguration deviceConfiguration, ConnectivityInformation connectivityInformation) {
        this(null, null, executorService,
                Coerce.toString(deviceConfiguration.getThingName()) + CIS_SHADOW_SUFFIX,
                connectivityInformation);
        this.connection = new WrapperMqttClientConnection(mqttClient);
        this.iotShadowClient = new IotShadowClient(this.connection);
    }

    CISShadowMonitor(MqttClientConnection connection, IotShadowClient iotShadowClient, ExecutorService executorService,
                     String shadowName, ConnectivityInformation connectivityInformation) {
        this.connection = connection;
        this.iotShadowClient = iotShadowClient;
        this.executorService = executorService;
        this.shadowName = shadowName;
        this.connectivityInformation = connectivityInformation;
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
        if (processShadowTasks != null) {
            processShadowTasks.cancel(true);
        }
        processShadowTasks = executorService.submit(this::processShadowTasks);
    }

    /**
     * Stop shadow monitor.
     */
    public void stopMonitor() {
        if (processShadowTasks != null) {
            processShadowTasks.cancel(true);
        }
        if (subscribeTaskFuture != null) {
            subscribeTaskFuture.cancel(true);
        }
        unsubscribeFromShadowTopics();
        synchronized (shadowTaskLock) {
            if (shadowTask != null) {
                shadowTask.cancel(true);
                queue.clear();
            }
        }
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
                ShadowDeltaUpdatedSubscriptionRequest shadowDeltaUpdatedSubscriptionRequest =
                        new ShadowDeltaUpdatedSubscriptionRequest();
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
                                QualityOfService.AT_LEAST_ONCE, this::processCISShadow,
                                (e) -> LOGGER.atError().log("Error processing getShadowSubscription Response", e))
                        .get(TIMEOUT_FOR_SUBSCRIBING_TO_TOPICS_SECONDS, TimeUnit.SECONDS);
                LOGGER.info("Subscribed to shadow get accepted topic");
                return;

            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof MqttException || cause instanceof TimeoutException) {
                    LOGGER.atWarn().setCause(cause)
                            .log("Caught exception while subscribing to shadow topics, will retry shortly");
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
        // this method is run on a CRT thread, we must not block
        queue.queue(new ProcessCISShadowTask(response.version, response.state.desired));
    }

    private void processCISShadow(ShadowDeltaUpdatedEvent event) {
        // this method is run on a CRT thread, we must not block
        queue.queue(new ProcessCISShadowTask(event.version, event.state));
    }

    private void processShadowTasks() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                waitForShadowTaskToComplete(1000);
                ProcessCISShadowTask task = queue.poll();
                synchronized (shadowTaskLock) {
                    if (!isShadowTaskComplete()) {
                        queue.queue(task);
                        continue;
                    }
                    shadowTask = executorService.submit(task);
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // TODO wait on condition instead of sleeping
    private void waitForShadowTaskToComplete(long timeBetweenChecksMillis) throws InterruptedException {
        boolean complete = false;
        while (!complete && !Thread.currentThread().isInterrupted()) {
            complete = isShadowTaskComplete();
            if (!complete) {
                Thread.sleep(timeBetweenChecksMillis);
            }
        }
    }

    private boolean isShadowTaskComplete() {
        synchronized (shadowTaskLock) {
            return shadowTask == null || shadowTask.isDone();
        }
    }

    /**
     * Asynchronously update the CIS shadow's reported state for the given shadow version.
     * We don't rely on reported state, so this method will log on failure and not throw.
     *
     * @param reportedState CIS shadow reported state
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void updateCISShadowReportedState(Map<String, Object> reportedState) {
        try {
            UpdateShadowRequest updateShadowRequest = new UpdateShadowRequest();
            updateShadowRequest.thingName = shadowName;
            updateShadowRequest.state = new ShadowState();
            updateShadowRequest.state.reported = reportedState == null ? null : new HashMap<>(reportedState);
            iotShadowClient.PublishUpdateShadow(updateShadowRequest, QualityOfService.AT_LEAST_ONCE)
                    .exceptionally(e -> {
                        LOGGER.atWarn().cause(e).log("Unable to update CIS shadow reported state");
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.atWarn().cause(e).log("Unable to send PublishUpdateShadow request");
        }
    }

    private void publishToGetCISShadowTopic() {
        LOGGER.atDebug().log("Publishing to get shadow topic");
        GetShadowRequest getShadowRequest = new GetShadowRequest();
        getShadowRequest.thingName = shadowName;
        iotShadowClient.PublishGetShadow(getShadowRequest, QualityOfService.AT_LEAST_ONCE).exceptionally(e -> {
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
            publishToGetCISShadowTopic();
        }
    }

    private static class CISShadowProcessingQueue {

        /**
         * Lock for ensuring synchronous access to {@link CISShadowProcessingQueue#task}.
         */
        private final ReentrantLock lock = new ReentrantLock();
        /**
         * Condition to signal threads waiting for queue to contain data.
         */
        private final Condition notEmpty = lock.newCondition();
        /**
         * Queued-up task.
         */
        private ProcessCISShadowTask task;

        /**
         * Add the task to the queue. This is a non-blocking operation.
         *
         * @param task task to queue
         */
        void queue(ProcessCISShadowTask task) {
            lock.lock();
            try {
                ProcessCISShadowTask existing = this.task;
                if (existing == null) {
                    this.task = task;
                    signalIfNotEmpty();
                } else if (task.getShadowVersion() > existing.getShadowVersion()) {
                    this.task = task;
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * Pull a task from the queue.  This is a blocking operation.
         *
         * @return task
         * @throws InterruptedException if interrupted while waiting for a task
         */
        ProcessCISShadowTask poll() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                waitIfEmpty();
                ProcessCISShadowTask t = this.task;
                clear();
                return t;
            } finally {
                lock.unlock();
            }
        }

        void clear() {
            lock.lock();
            try {
                this.task = null;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Tell thread to wait until the queue is not empty.
         */
        private void waitIfEmpty() throws InterruptedException {
            while (isEmpty()) {
                notEmpty.await();
            }
        }

        private void signalIfNotEmpty() {
            if (!isEmpty()) {
                notEmpty.signal();
            }
        }

        private boolean isEmpty() {
            lock.lock();
            try {
                return task == null;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Task for processing the CIS shadow. The goal is to rotate
     * certificates if we detect that connectivity info has changed.
     * This task is triggered by
     */
    private class ProcessCISShadowTask implements Runnable {

        @Getter
        private final int shadowVersion;
        private final Map<String, Object> desiredState;

        ProcessCISShadowTask(int shadowVersion, Map<String, Object> desiredState) {
            this.shadowVersion = shadowVersion;
            this.desiredState = desiredState;
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        public void run() {
            try {
                processCISShadow();
            } catch (InterruptedException ignored) {
                // logged in processCISShadow
            } catch (Exception e) {
                // retry
                queue.queue(this);
            }
        }

        @SuppressWarnings({"PMD.AvoidCatchingGenericException",
                "PMD.SignatureDeclareThrowsException", "PMD.PrematureDeclaration"})
        public void processCISShadow() throws Exception {
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
                LOGGER.atDebug().cause(e).log("Retry workflow for getting connectivity info interrupted");
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                LOGGER.atError().cause(e)
                        .log("Failed to get connectivity info from cloud. Check that the core device's IoT policy "
                                + "grants the greengrass:GetConnectivityInfo permission");
                throw e;
            }

            if (!connectivityInfo.isPresent()) {
                // CIS call failed for either ValidationException or ResourceNotFoundException.
                // We won't retry in this case, but we will update the CIS shadow reported state
                // to signal that we have fully processed this version.
                LOGGER.atInfo().log("No connectivity info found. Skipping cert re-generation");
                updateCISShadowReportedState(desiredState);
                return;
            }

            // skip cert rotation if connectivity info hasn't changed
            Set<String> cachedHostAddresses = new HashSet<>(connectivityInformation.getCachedHostAddresses());
            if (Objects.equals(prevCachedHostAddresses, cachedHostAddresses)) {
                LOGGER.atInfo().log("Connectivity info hasn't changed. Skipping cert re-generation");
                // update the CIS shadow reported state
                // to signal that we have fully processed this version.
                updateCISShadowReportedState(desiredState);
                return;
            }

            LOGGER.atInfo().log("Connectivity info change detected, rotating certificates");

            try {
                for (CertificateGenerator cg : monitoredCertificateGenerators) {
                    cg.generateCertificate(() -> new ArrayList<>(cachedHostAddresses),
                            "connectivity info was updated");
                }
            } catch (CertificateGenerationException e) {
                LOGGER.atError().cause(e).log("Failed to generate new certificates");
                throw e;
            }

            // update CIS shadow so reported state matches desired state
            updateCISShadowReportedState(desiredState);
        }
    }
}
