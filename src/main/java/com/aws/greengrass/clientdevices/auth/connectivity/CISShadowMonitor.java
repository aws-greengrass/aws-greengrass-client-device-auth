/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.mqttclient.MqttClient;
import com.aws.greengrass.mqttclient.WrapperMqttClientConnection;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.CrashableSupplier;
import com.aws.greengrass.util.RetryUtils;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

@SuppressWarnings("PMD.ImmutableField")
public class CISShadowMonitor implements Consumer<NetworkStateProvider.ConnectionState> {
    private static final Logger LOGGER = LogManager.getLogger(CISShadowMonitor.class);
    private static final String KV_TASK = "task";
    private static final String CIS_SHADOW_SUFFIX = "-gci";
    private static final RetryUtils.RetryConfig ALWAYS_RETRY_CONFIG =
            RetryUtils.RetryConfig.builder()
                    .initialRetryInterval(Duration.ofSeconds(1L))
                    .maxRetryInterval(Duration.ofSeconds(30L))
                    .maxAttempt(Integer.MAX_VALUE)
                    .retryableExceptions(Collections.singletonList(Exception.class))
                    .build();
    private final CISShadowTaskExecutor taskExecutor = new CISShadowTaskExecutor();
    private final CISShadowTaskQueue taskQueue = new CISShadowTaskQueue();

    private final Consumer<ShadowDeltaUpdatedEvent> onShadowDeltaUpdated = resp ->
            taskQueue.offer(new ProcessCISShadowTask(
                    resp.version, Coerce.toString(resp.state.get("version")), resp.state));
    private final Consumer<GetShadowResponse> onGetShadowAccepted = resp -> {
        signalShadowResponseReceived();
        taskQueue.offer(new ProcessCISShadowTask(
                resp.version, Coerce.toString(resp.state.desired.get("version")), resp.state.desired));
    };
    private final Consumer<ErrorResponse> onGetShadowRejected = err -> signalShadowResponseReceived();

    private Future<?> getShadowTask;
    AtomicReference<CompletableFuture<?>> getShadowResponseReceived = new AtomicReference<>();

    private final Supplier<Integer> mqttOperationTimeoutMillis;
    private MqttClientConnection connection;
    private IotShadowClient iotShadowClient;
    private final NetworkStateProvider networkStateProvider;
    private final List<CertificateGenerator> monitoredCertificateGenerators = new CopyOnWriteArrayList<>();
    private final ExecutorService executorService;
    private final AtomicReference<String> shadowName = new AtomicReference<>();
    private final ConnectivityInformation connectivityInformation;

    private final SucceedOnceOperation subscribeToShadowUpdateDelta = new SucceedOnceOperation(() -> {
        ShadowDeltaUpdatedSubscriptionRequest shadowDeltaUpdatedSubscriptionRequest =
                new ShadowDeltaUpdatedSubscriptionRequest();
        shadowDeltaUpdatedSubscriptionRequest.thingName = shadowName.get();
        iotShadowClient.SubscribeToShadowDeltaUpdatedEvents(
                        shadowDeltaUpdatedSubscriptionRequest,
                        QualityOfService.AT_LEAST_ONCE,
                        onShadowDeltaUpdated,
                        (e) -> LOGGER.atError()
                                .log("Error processing shadowDeltaUpdatedSubscription Response", e))
                .get();
        LOGGER.atDebug().log("Subscribed to shadow update delta topic");
        return null;
    });

    private final SucceedOnceOperation subscribeToShadowGetAccepted = new SucceedOnceOperation(() -> {
        GetShadowSubscriptionRequest getShadowSubscriptionRequest = new GetShadowSubscriptionRequest();
        getShadowSubscriptionRequest.thingName = shadowName.get();
        iotShadowClient.SubscribeToGetShadowAccepted(
                        getShadowSubscriptionRequest,
                        QualityOfService.AT_LEAST_ONCE,
                        onGetShadowAccepted,
                        (e) -> LOGGER.atError().log("Error processing getShadowSubscription Response", e))
                .get();
        LOGGER.atDebug().log("Subscribed to shadow get accepted topic");
        return null;
    });

    private final SucceedOnceOperation subscribeToShadowGetRejected = new SucceedOnceOperation(() -> {
        GetShadowSubscriptionRequest getShadowRejectedSubscriptionRequest = new GetShadowSubscriptionRequest();
        getShadowRejectedSubscriptionRequest.thingName = shadowName.get();
        iotShadowClient.SubscribeToGetShadowRejected(
                        getShadowRejectedSubscriptionRequest,
                        QualityOfService.AT_LEAST_ONCE,
                        onGetShadowRejected,
                        (e) -> LOGGER.atError().log("Error processing get shadow rejected response", e))
                .get();
        LOGGER.atDebug().log("Subscribed to shadow get rejected topic");
        return null;
    });

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
        this.shadowName.set(shadowName);
        this.connectivityInformation = connectivityInformation;
        this.mqttOperationTimeoutMillis = mqttOperationTimeoutMillis;
    }

    /**
     * Start shadow monitor.
     */
    public void startMonitor() {
        taskExecutor.start();
        fetchCISShadowAsync();
    }

    /**
     * Stop shadow monitor.
     */
    public void stopMonitor() {
        cancelFetchCISShadow();
        taskExecutor.stop();
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

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private synchronized void fetchCISShadowAsync() {
        if (networkStateProvider.getConnectionState() == NetworkStateProvider.ConnectionState.NETWORK_DOWN) {
            // will be retried when online again
            return;
        }
        if (getShadowTask != null && !getShadowTask.isDone()) {
            // operation already in progress
            return;
        }
        getShadowTask = executorService.submit(() -> {
            try {
                RetryUtils.runWithRetry(
                        ALWAYS_RETRY_CONFIG,
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
                RetryUtils.runWithRetry(
                        ALWAYS_RETRY_CONFIG,
                        () -> {
                            CompletableFuture<?> shadowGetResponseReceived = new CompletableFuture<>();
                            this.getShadowResponseReceived.set(shadowGetResponseReceived);
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

    private synchronized void cancelFetchCISShadow() {
        if (getShadowTask != null) {
            getShadowTask.cancel(true);
        }
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    private void subscribeToShadowTopics() throws Exception {
        subscribeToShadowUpdateDelta.apply();
        subscribeToShadowGetAccepted.apply();
        subscribeToShadowGetRejected.apply();
    }

    /**
     * Called when a shadow response, either accepted or rejected, is received.
     */
    private void signalShadowResponseReceived() {
        CompletableFuture<?> shadowReceived = this.getShadowResponseReceived.get();
        if (shadowReceived != null) {
            shadowReceived.complete(null);
        }
    }

    private CompletableFuture<Integer> publishToGetCISShadowTopic() {
        LOGGER.atDebug().log("Publishing to get shadow topic");
        GetShadowRequest getShadowRequest = new GetShadowRequest();
        getShadowRequest.thingName = shadowName.get();
        return iotShadowClient.PublishGetShadow(getShadowRequest, QualityOfService.AT_MOST_ONCE)
                .exceptionally(e -> {
                    LOGGER.atWarn().cause(e).log("Unable to retrieve CIS shadow");
                    return null;
                });
    }

    @Override
    public void accept(NetworkStateProvider.ConnectionState state) {
        if (state == NetworkStateProvider.ConnectionState.NETWORK_UP) {
            fetchCISShadowAsync();
        } else if (state == NetworkStateProvider.ConnectionState.NETWORK_DOWN) {
            cancelFetchCISShadow();
        }
    }

    @ToString
    @EqualsAndHashCode
    @RequiredArgsConstructor
    class ProcessCISShadowTask implements Callable<Void> {
        private final long shadowVersion;
        private final String cisVersion;
        private final Map<String, Object> desiredState;

        @Override
        public Void call() throws Exception {
            LOGGER.atDebug().kv(KV_TASK, this).log("Processing CIS shadow");
            try {
                processCISShadow();
                LOGGER.atDebug().kv(KV_TASK, this).log("Shadow processing complete");
                return null;
            } finally {
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.atDebug().kv(KV_TASK, this)
                            .log("Shadow processing interrupted");
                } else {
                    // always attempt to report shadow update state in case it failed to update previously.
                    // NOTE: setting reported state is not required, functionality-wise.
                    //       however, it may be useful for debugging
                    updateCISShadowReportedState(desiredState);
                }
            }
        }

        @SuppressWarnings({"PMD.AvoidCatchingGenericException",
                "PMD.SignatureDeclareThrowsException", "PMD.PrematureDeclaration"})
        private void processCISShadow() throws Exception {
            Set<String> prevCachedHostAddresses = new HashSet<>(connectivityInformation.getCachedHostAddresses());

            Optional<List<ConnectivityInfo>> connectivityInfo = RetryUtils.runWithRetry(
                    ALWAYS_RETRY_CONFIG,
                    connectivityInformation::getConnectivityInfo,
                    "get-connectivity",
                    LOGGER
            );

            if (!connectivityInfo.isPresent()) {
                // CIS call failed for either ValidationException or ResourceNotFoundException.
                // We won't retry in this case, but we will update the CIS shadow reported state
                // to signal that we have fully processed this version.
                LOGGER.atDebug().kv(KV_TASK, this)
                        .log("No connectivity info found. Skipping cert re-generation");
                return;
            }

            // skip cert rotation if connectivity info hasn't changed
            Set<String> cachedHostAddresses = new HashSet<>(connectivityInformation.getCachedHostAddresses());
            if (Objects.equals(prevCachedHostAddresses, cachedHostAddresses)) {
                LOGGER.atDebug().kv(KV_TASK, this)
                        .log("Connectivity info hasn't changed. Skipping cert re-generation");
                return;
            }

            List<SucceedOnceOperation> certGenOperations = monitoredCertificateGenerators.stream()
                    .map(cg -> generateCertificateOperation(cg, cachedHostAddresses))
                    .collect(Collectors.toList());
            RetryUtils.runWithRetry(
                    ALWAYS_RETRY_CONFIG,
                    () -> generateCertificates(certGenOperations),
                    "generate-certificates",
                    LOGGER
            );
        }

        private SucceedOnceOperation generateCertificateOperation(CertificateGenerator cg, Set<String> hostAddresses) {
            return new SucceedOnceOperation(() -> {
                cg.generateCertificate(
                        () -> new ArrayList<>(hostAddresses), "connectivity info was updated");
                return null;
            });
        }

        @SuppressWarnings("PMD.SignatureDeclareThrowsException")
        private Void generateCertificates(List<SucceedOnceOperation> certGenOperations) throws Exception {
            for (SucceedOnceOperation genCert : certGenOperations) {
                // cert gen may be expensive, so catch interrupts as early as possible
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Interrupted while generating certificates");
                }
                genCert.apply();
            }
            return null;
        }

        private void updateCISShadowReportedState(Map<String, Object> reportedState) {
            UpdateShadowRequest updateShadowRequest = new UpdateShadowRequest();
            updateShadowRequest.thingName = shadowName.get();
            updateShadowRequest.state = new ShadowState();
            updateShadowRequest.state.reported = reportedState == null ? null : new HashMap<>(reportedState);
            iotShadowClient.PublishUpdateShadow(updateShadowRequest, QualityOfService.AT_LEAST_ONCE)
                    .exceptionally(e -> {
                        LOGGER.atDebug().cause(e).log("Unable to update CIS shadow reported state");
                        return null;
                    });
        }
    }

    class CISShadowTaskExecutor {
        private Future<?> task;

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        void start() {
            stop();
            task = executorService.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    ProcessCISShadowTask task = null;
                    try {
                        // keep task in queue during processing
                        task = taskQueue.blockingPeek();
                        task.call();
                        taskQueue.removeProcessedTask(task);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Exception e) {
                        LOGGER.atError().cause(e).kv(KV_TASK, task).log("Unable to process connectivity shadow. "
                                + "Certificates may not be updated with most current connectivity info. "
                                + "If components are experiencing TLS handshake errors, restart Greengrass "
                                + "to refresh certificates");
                    }
                }
            });
        }

        void stop() {
            if (task != null) {
                task.cancel(true);
            }
        }
    }

    /**
     * A queue for connectivity shadow responses. Old shadows will be rejected from the queue.
     */
    class CISShadowTaskQueue  {
        private final BlockingDeque<ProcessCISShadowTask> tasks = new LinkedBlockingDeque<>();
        private ProcessCISShadowTask lastProcessedTask;
        private final Comparator<ProcessCISShadowTask> taskComparator = (task1, task2) -> {
            if (Objects.equals(task1, task2)) {
                return 0;
            }

            // previous or duplicate shadow version
            if (task1.shadowVersion <= task2.shadowVersion) {
                return -1;
            }

            // duplicate shadow state
            if (Objects.equals(task2.cisVersion, task1.cisVersion)) {
                return -1;
            }

            // task2 is a new request
            return 1;
        };

        synchronized boolean offer(@NonNull ProcessCISShadowTask task) {
            if (!isNewTask(task)) {
                LOGGER.atDebug().kv(KV_TASK, task).log("Ignoring CIS shadow");
                return false;
            }
            // since we received a newer request, we can discard all older ones,
            // except the head of the queue, which is the task currently being processed.
            while (tasks.size() > 1) {
                tasks.pollLast();
            }
            return tasks.offerLast(task);
        }

        private boolean isNewTask(ProcessCISShadowTask task) {
            return Stream.of(this.tasks.peekLast(), lastProcessedTask)
                    .allMatch(t -> t == null || taskComparator.compare(task, t) > 0);
        }

        synchronized void removeProcessedTask(ProcessCISShadowTask task) {
            lastProcessedTask = task;
            tasks.remove(task);
        }

        /**
         * Like {@link BlockingDeque#peek()} except it blocks until the queue has at least one element.
         *
         * @return head of the queue
         * @throws InterruptedException if interrupted while waiting
         */
        ProcessCISShadowTask blockingPeek() throws InterruptedException {
            ProcessCISShadowTask task = tasks.takeFirst();
            synchronized (this) {
                // elements may have been added to the queue,
                // recalculate the head of the queue
                ProcessCISShadowTask head = tasks.pollFirst();
                if (head != null && taskComparator.compare(head, task) > 0) {
                    task = head;
                }
                tasks.offerFirst(task);
            }
            return task;
        }
    }

    static class SucceedOnceOperation implements CrashableSupplier<Void, Exception> {
        private boolean succeededOnce;
        private final CrashableSupplier<Void, Exception> operation;

        SucceedOnceOperation(@NonNull CrashableSupplier<Void, Exception> operation) {
            this.operation = operation;
        }

        /**
         * Perform the operation if it has not yet succeeded once already.
         *
         * @return nothing
         * @throws Exception on failure
         */
        @Override
        public Void apply() throws Exception {
            if (succeededOnce) {
                return null;
            }
            operation.apply();
            succeededOnce = true;
            return null;
        }
    }
}
