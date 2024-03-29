/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.helpers.TestHelpers;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.NetworkStateFake;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.Pair;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.Timestamp;
import software.amazon.awssdk.iot.iotshadow.IotShadowClient;
import software.amazon.awssdk.iot.iotshadow.model.ErrorResponse;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowResponse;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedEvent;
import software.amazon.awssdk.iot.iotshadow.model.ShadowStateWithDelta;
import software.amazon.awssdk.iot.iotshadow.model.UpdateShadowRequest;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class CISShadowMonitorTest {
    private static final List<Class<? extends Exception>> IGNORED_EXCEPTIONS = Arrays.asList(
            ValidationException.class,
            RuntimeException.class,
            TimeoutException.class,
            ExecutionException.class,
            InterruptedException.class
    );
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SHADOW_NAME = "testThing-gci";
    private static final String UPDATE_SHADOW_TOPIC = String.format("$aws/things/%s/shadow/update", SHADOW_NAME);
    private final NetworkStateFake networkStateProvider = new NetworkStateFake();
    private final FakeIotShadowClient shadowClient = spy(new FakeIotShadowClient(networkStateProvider));
    private final MqttClientConnection shadowClientConnection = shadowClient.getConnection();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService ses = Executors.newScheduledThreadPool(0);
    private final FakeConnectivityInformation connectivityInfoProvider = new FakeConnectivityInformation();

    @Mock
    CertificateGenerator certificateGenerator;
    CISShadowMonitor cisShadowMonitor;

    @BeforeEach
    void setup() {
        cisShadowMonitor = new CISShadowMonitor(
                networkStateProvider,
                shadowClientConnection,
                shadowClient,
                executor,
                SHADOW_NAME,
                connectivityInfoProvider,
                () -> 100
        );
        networkStateProvider.registerHandler(cisShadowMonitor);
    }

    @AfterEach
    void tearDown() {
        cisShadowMonitor.stopMonitor();
        executor.shutdownNow();
        ses.shutdownNow();
    }

    @Data
    @Builder
    static class Scenario {
        /**
         * Amount of times to update the CIS shadow's desired state,
         * which will trigger a delta update that will be picked up
         * by the monitor.
         */
        @Builder.Default
        int numShadowUpdates = 5;

        /**
         * Delay between each subsequent shadow update publish.
         */
        Duration delayBetweenShadowUpdates;

        /**
         * Controls if each of the {@link CISShadowMonitorTest.Scenario#numShadowUpdates} updates
         * happens serially or if they can overlap.
         */
        boolean serialShadowUpdates;

        /**
         * Amount of times to fail the monitor's attempt to
         * publish CIS shadow reported state.
         */
        int numShadowUpdatePublishFailures;

        /**
         * If present, shadow will be created after the monitor starts.
         */
        Duration createShadowAfterDelay;

        /**
         * If present, CDA will go online after the monitor starts.
         */
        Duration goOnlineAfterDelay;

        /**
         * If present, CDA will go offline after the monitor starts.
         */
        Duration goOfflineAfterDelay;

        /**
         * How long it takes to generate a certificate
         */
        Duration certGenDuration;

        /**
         * If true, simulate monitor receiving duplicate
         * shadow delta update messages from IoT Core.
         *
         * <p> This is useful for testing if the monitor properly de-duplicates
         * update requests.
         */
        boolean receiveDuplicateShadowDeltaUpdates;

        /**
         * Simulate how monitor interacts with Greengrass cloud when fetching connectivity information.
         */
        @Builder.Default
        FakeConnectivityInformation.Mode connectivityProviderMode = FakeConnectivityInformation.Mode.RANDOM;
    }

    public static Stream<Arguments> cisShadowMonitorScenarios() {
        return Stream.of(
                Arguments.of(Scenario.builder()
                        .build()),
                Arguments.of(Scenario.builder()
                        .receiveDuplicateShadowDeltaUpdates(true)
                        .build()),
                // when monitor starts while cda is offline,
                // shadow will update after online
                Arguments.of(Scenario.builder()
                        .goOnlineAfterDelay(Duration.ofSeconds(2L))
                        .build()),
                // test that monitor recovers after being offline during operation
                Arguments.of(Scenario.builder()
                        .numShadowUpdates(5)
                        .delayBetweenShadowUpdates(Duration.ofSeconds(1))
                        .goOfflineAfterDelay(Duration.ofSeconds(2L))
                        .goOnlineAfterDelay(Duration.ofSeconds(6L))
                        .build()),
                // when monitor can't get shadow on startup,
                // it'll recover on subsequent shadow updates
                Arguments.of(Scenario.builder()
                        .createShadowAfterDelay(Duration.ofSeconds(2L))
                        .serialShadowUpdates(true)
                        .build()),
                // if shadow is never updated,
                // monitor still works because it fetches shadow on startup
                Arguments.of(Scenario.builder()
                        .numShadowUpdates(0)
                        .build()),
                // if shadow is never updated,
                // monitor still works because it fetches shadow on startup.
                // if shadow fetching fails, it will be retried
                Arguments.of(Scenario.builder()
                        .numShadowUpdates(0)
                        .createShadowAfterDelay(Duration.ofSeconds(2L))
                        .serialShadowUpdates(true)
                        .build()),
                Arguments.of(Scenario.builder()
                        .numShadowUpdatePublishFailures(1)
                        .serialShadowUpdates(true)
                        .build()),
                Arguments.of(Scenario.builder()
                        .connectivityProviderMode(FakeConnectivityInformation.Mode.THROW_VALIDATION_EXCEPTION)
                        .build()),
                Arguments.of(Scenario.builder()
                        .connectivityProviderMode(FakeConnectivityInformation.Mode.CONSTANT)
                        .build()),
                Arguments.of(Scenario.builder()
                        .connectivityProviderMode(FakeConnectivityInformation.Mode.CONSTANT)
                        .receiveDuplicateShadowDeltaUpdates(true)
                        .build()),
                Arguments.of(Scenario.builder()
                        .connectivityProviderMode(FakeConnectivityInformation.Mode.FAIL_ONCE)
                        .serialShadowUpdates(true)
                        .build()),
                Arguments.of(Scenario.builder()
                        // force situation where request is received while another is in progress
                        .certGenDuration(Duration.ofSeconds(3))
                        .receiveDuplicateShadowDeltaUpdates(true)
                        .build())
        );
    }

    @ParameterizedTest
    @MethodSource("cisShadowMonitorScenarios")
    @SuppressWarnings("PMD.NullAssignment")
    void GIVEN_monitor_WHEN_cis_shadow_changes_THEN_monitor_updates_certificates(Scenario scenario, ExtensionContext context) throws Exception {
        IGNORED_EXCEPTIONS.forEach(e -> ignoreExceptionOfType(context, e));

        if (scenario.certGenDuration != null) {
            doAnswer(invocation -> {
                Thread.sleep(scenario.certGenDuration.toMillis());
                return null;
            }).when(certificateGenerator).generateCertificate(any(), any());
        }

        connectivityInfoProvider.setMode(scenario.getConnectivityProviderMode());

        // latch that completes when the monitor sends a shadow update.
        // this reference holds a new latch per request processed by the monitor,
        // and is used by this test to (optionally) feed messages to the monitor serially
        AtomicReference<CountDownLatch> updateProcessedByMonitor = updateProcessedByMonitor();

        Runnable putInitialShadowState = () -> updateShadowDesiredState(
                Utils.immutableMap("version", "-1"),
                scenario.isReceiveDuplicateShadowDeltaUpdates()
        );

        // optional, handle case where shadow exists before monitor starts up
        if (scenario.getCreateShadowAfterDelay() == null) {
            putInitialShadowState.run();
        }

        // by default, start online
        if (scenario.getGoOnlineAfterDelay() == null || scenario.getGoOfflineAfterDelay() != null) {
            networkStateProvider.goOnline();
        }

        cisShadowMonitor.addToMonitor(certificateGenerator);
        cisShadowMonitor.startMonitor();

        // optional, handle case where shadow is created AFTER monitor is started
        if (scenario.getCreateShadowAfterDelay() != null) {
            if (!scenario.isSerialShadowUpdates()) {
                fail("initializing shadow after monitor startup "
                        + "only supported when shadow updates are serialized, otherwise there's no point");
            }
            ses.schedule(putInitialShadowState, scenario.getCreateShadowAfterDelay().toMillis(), TimeUnit.MILLISECONDS);
        }
        // optional, trigger network up after a delay
        if (scenario.getGoOnlineAfterDelay() != null) {
            ses.schedule(networkStateProvider::goOnline, scenario.getGoOnlineAfterDelay().toMillis(), TimeUnit.MILLISECONDS);
        }
        // optional, trigger network down after a delay
        if (scenario.getGoOfflineAfterDelay() != null) {
            ses.schedule(networkStateProvider::goOffline, scenario.getGoOfflineAfterDelay().toMillis(), TimeUnit.MILLISECONDS);
        }

        // on startup, the monitor directly requests a shadow and processes it.
        // optionally wait for the monitor to process the get shadow response.
        if (scenario.isSerialShadowUpdates()) {
            boolean monitorExpectedToUpdateReportedState =
                    scenario.getConnectivityProviderMode() != FakeConnectivityInformation.Mode.FAIL_ONCE
                            && scenario.getNumShadowUpdatePublishFailures() == 0;
            waitForSubscriptions();
            waitForMonitorToProcessUpdate(updateProcessedByMonitor, monitorExpectedToUpdateReportedState);
        }

        for (int i = 0; i < scenario.getNumShadowUpdates(); i++) {
            // optionally have monitor fail to update shadow reported state
            boolean monitorExpectedToUpdateReportedState = i >= scenario.getNumShadowUpdatePublishFailures();
            shadowClient.failOnPublish(monitorExpectedToUpdateReportedState ? null: UPDATE_SHADOW_TOPIC);

            // trigger a shadow delta update,
            // which will be picked up by the monitor
            updateShadowDesiredState(
                    Utils.immutableMap("version", String.valueOf(i)),
                    scenario.isReceiveDuplicateShadowDeltaUpdates()
            );

            // optionally wait for monitor to process the update
            if (scenario.isSerialShadowUpdates()) {
                waitForMonitorToProcessUpdate(updateProcessedByMonitor, monitorExpectedToUpdateReportedState);
            }
            if (scenario.getDelayBetweenShadowUpdates() != null) {
                Thread.sleep(scenario.getDelayBetweenShadowUpdates().toMillis());
            }
        }

        assertShadowEventuallyEquals(
                Utils.immutableMap("version", String.valueOf(scenario.getNumShadowUpdates() - 1)),
                Utils.immutableMap("version", String.valueOf(scenario.getNumShadowUpdates() - 1)));

        verifyCertsRotatedWhenConnectivityChanges();
    }

    private AtomicReference<CountDownLatch> updateProcessedByMonitor() {
        AtomicReference<CountDownLatch> updateProcessedByMonitor = new AtomicReference<>(new CountDownLatch(1));
        shadowClient.onPublish(m -> {
            if (!Objects.equals(m.getTopic(), UPDATE_SHADOW_TOPIC)) {
                return;
            }
            UpdateShadowRequest request = readValue(m, UpdateShadowRequest.class);
            // differentiate publishes made by this test and the monitor
            boolean isUpdateRequestFromMonitor = request.state.reported != null && request.state.desired == null;
            if (!isUpdateRequestFromMonitor) {
                return;
            }
            updateProcessedByMonitor.getAndSet(new CountDownLatch(1)).countDown();
        });
        return updateProcessedByMonitor;
    }

    private void waitForMonitorToProcessUpdate(AtomicReference<CountDownLatch> updateProcessedByMonitor, boolean monitorExpectedToUpdateReportedState) {
        try {
            if (monitorExpectedToUpdateReportedState) {
                // wait for monitor to update shadow state, which
                // means that it has finished processing that particular shadow version
                assertTrue(updateProcessedByMonitor.get().await(15L, TimeUnit.SECONDS));
            } else {
                // monitor will not update the shadow state,
                // so we don't have a way to know when the monitor has completed its work
                // without cracking into monitor internals.
                // sleeping is inefficient but it'll work well enough.
                Thread.sleep(1000L);
            }
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    private void waitForSubscriptions() throws InterruptedException {
        TestHelpers.eventuallyTrue(() -> shadowClient.subscriptions.size() == 3);
    }

    private void updateShadowDesiredState(Map<String, Object> reported, boolean publishDuplicateMessage) {
        shadowClient.updateShadow(
                SHADOW_NAME,
                reported,
                null,
                publishDuplicateMessage
        );
    }

    private void assertShadowEventuallyEquals(Map<String, Object> desired, Map<String, Object> reported) throws InterruptedException {
        TestHelpers.eventuallyTrue(() ->
                Objects.equals(shadowClient.getShadow(SHADOW_NAME).getLeft(), desired) &&
                        Objects.equals(shadowClient.getShadow(SHADOW_NAME).getRight(), reported)
        );
    }

    /**
     * Verify that certificates are rotated only when connectivity info changes.
     *
     * @throws CertificateGenerationException n/a
     */
    private void verifyCertsRotatedWhenConnectivityChanges() throws CertificateGenerationException {
        verify(certificateGenerator,
                times(connectivityInfoProvider.getNumConnectivityInfoChanges())).generateCertificate(any(),
                any());
    }

    @Nonnull
    private static <T> T readValue(MqttMessage message, Class<T> clazz) {
        try {
            return MAPPER.readValue(message.getPayload(), clazz);
        } catch (IOException e) {
            fail(String.format("unable to read payload of type %s", clazz.getSimpleName()), e);
            return null;
        }
    }

    private static <T> MqttMessage asMessage(String topic, T payload) {
        return asMessage(topic, payload, false);
    }

    private static <T> MqttMessage asMessage(String topic, T payload, boolean dup) {
        try {
            return new MqttMessage(topic, MAPPER.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8),
                    QualityOfService.AT_LEAST_ONCE, false, dup);
        } catch (JsonProcessingException e) {
            return fail(e);
        }
    }

    static class FakeIotShadowClient extends IotShadowClient {
        private static final CompletableFuture<Integer> DUMMY_PACKET_ID = CompletableFuture.completedFuture(0);
        private final Map<String, Shadow> shadowsByThingName = Collections.synchronizedMap(new HashMap<>());
        private final Map<String, Consumer<MqttMessage>> subscriptions = Collections.synchronizedMap(new HashMap<>());
        private final AtomicReference<String> failOnPublish = new AtomicReference<>();
        private final AtomicReference<Pair<String, AtomicInteger>> failOnGet = new AtomicReference<>();
        private final List<Consumer<MqttMessage>> onPublish = new ArrayList<>();

        @Getter(AccessLevel.PACKAGE)
        private final MqttClientConnection connection;
        private final NetworkStateProvider networkStateProvider;

        FakeIotShadowClient(NetworkStateProvider networkStateProvider) {
            this(mock(MqttClientConnection.class), networkStateProvider);
        }

        private FakeIotShadowClient(MqttClientConnection connection,
                                    NetworkStateProvider networkStateProvider) {
            super(connection);
            this.networkStateProvider = networkStateProvider;
            this.connection = connection;
            when(this.connection.subscribe(any(), any(), any())).thenAnswer(invocation -> {
                if (isOffline()) {
                    return offlineResult();
                }
                String topic = invocation.getArgument(0, String.class);
                Consumer<MqttMessage> callback = invocation.getArgument(2);
                subscriptions.put(topic, callback);
                return DUMMY_PACKET_ID;
            });
            when(this.connection.publish(any(), any(), anyBoolean())).thenAnswer(invocation -> {
                if (isOffline()) {
                    return offlineResult();
                }
                MqttMessage message = invocation.getArgument(0, MqttMessage.class);
                String topic = message.getTopic();

                if (Objects.equals(topic, failOnPublish.get())) {
                    CompletableFuture<Integer> f = new CompletableFuture<>();
                    f.completeExceptionally(new RuntimeException());
                    return f;
                }

                if (topic.endsWith("shadow/get")) {
                    handleShadowGetRequest(message);
                } else if (topic.endsWith("shadow/update")) {
                    handleShadowUpdateRequest(message);
                }
                onPublish.forEach(cb -> cb.accept(message));
                return DUMMY_PACKET_ID;
            });
            when(this.connection.unsubscribe(any())).thenAnswer(invocation -> {
                if (isOffline()) {
                    return offlineResult();
                }
                String topic = invocation.getArgument(0, String.class);
                subscriptions.remove(topic);
                return DUMMY_PACKET_ID;
            });
        }

        private boolean isOffline() {
            return networkStateProvider.getConnectionState() == NetworkStateProvider.ConnectionState.NETWORK_DOWN;
        }

        private static CompletableFuture<?> offlineResult() {
            CompletableFuture<?> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("offline"));
            return f;
        }

        private void handleShadowGetRequest(MqttMessage message) {
            if (isOffline()) { // client not connected, don't fire subscriptions
                return;
            }

            String thingName = extractThingName(message.getTopic());
            Shadow shadow = shadowsByThingName.get(thingName);
            if (shadow == null) {
                return;
            }

            String respTopic;
            MqttMessage respMessage;

            if (failGetOperation(message.getTopic())) {
                ErrorResponse resp = new ErrorResponse();
                resp.message = "get shadow failed";
                resp.timestamp = new Timestamp(new Date());
                respTopic = rejectedTopic(thingName);
                respMessage = asMessage(respTopic, resp);
            } else {
                GetShadowResponse resp = new GetShadowResponse();
                resp.version = shadow.version;
                resp.state = new ShadowStateWithDelta();
                resp.state.desired = shadow.getDesired();
                resp.state.reported = shadow.getReported();
                resp.state.delta = shadow.getDelta();
                respTopic = acceptedTopic(thingName);
                respMessage = asMessage(respTopic, resp);
            }

            Consumer<MqttMessage> subscription = subscriptions.get(respTopic);
            if (subscription != null) {
                subscription.accept(respMessage);
            }
        }

        private boolean failGetOperation(String topic) {
            Pair<String, AtomicInteger> failOnGet = this.failOnGet.get();
            return failOnGet != null
                    && Objects.equals(topic, failOnGet.getLeft())
                    && failOnGet.getRight().getAndDecrement() > 0;
        }

        private void handleShadowUpdateRequest(MqttMessage message) {
            UpdateShadowRequest request = readValue(message, UpdateShadowRequest.class);
            updateShadow(
                    request.thingName,
                    request.state == null ? null : request.state.desired,
                    request.state == null ? null : request.state.reported
            );
        }

        void updateShadow(String thingName, Map<String, Object> desired, Map<String, Object> reported) {
            updateShadow(thingName, desired, reported, false);
        }

        @SuppressWarnings("PMD.PrematureDeclaration")
        void updateShadow(String thingName, Map<String, Object> desired, Map<String, Object> reported, boolean sendDuplicate) {
            Shadow updatedShadow = shadowsByThingName.compute(thingName, (k, v) -> {
                Shadow shadow = new Shadow(v);
                shadow.version++;
                if (desired != null) {
                    shadow.desired = new HashMap<>(desired);
                }
                if (reported != null) {
                    shadow.reported = new HashMap<>(reported);
                }
                shadow.recalculateDelta();
                return shadow;
            });

            if (isOffline()) { // client not connected, don't fire subscriptions
                return;
            }

            String shadowDeltaTopic = updateDeltaTopic(thingName);
            Consumer<MqttMessage> subscription = subscriptions.get(shadowDeltaTopic);
            if (subscription != null && !updatedShadow.getDelta().isEmpty()) {
                ShadowDeltaUpdatedEvent response = new ShadowDeltaUpdatedEvent();
                response.version = updatedShadow.getVersion();
                response.state = updatedShadow.getDelta();
                subscription.accept(asMessage(shadowDeltaTopic, response));
                if (sendDuplicate) {
                    subscription.accept(asMessage(shadowDeltaTopic, response, true));
                }
            }
        }

        /**
         * When a publish is made to the provided topic, fail the operation.
         *
         * @param topic topic
         */
        void failOnPublish(String topic) {
            failOnPublish.set(topic);
        }

        /**
         * When a get request is made to the provided topic, fail the operation.
         * Shadow get result will be sent to rejected topic instead of accepted topic.
         *
         * @param topic topic
         * @param times number of times to fail the get request
         */
        void failOnGet(String topic, int times) {
            failOnGet.set(new Pair<>(topic, new AtomicInteger(times)));
        }

        void onPublish(Consumer<MqttMessage> callback) {
            onPublish.add(callback);
        }

        /**
         * Retrieve shadow state by thing name.
         *
         * @param thingName thing name
         * @return desired and reported state
         */
        Pair<Map<String, Object>, Map<String, Object>> getShadow(String thingName) {
            Shadow shadow = shadowsByThingName.get(thingName);
            if (shadow == null) {
                return null;
            }
            return new Pair<>(shadow.getDesired(), shadow.getReported());
        }

        private static String acceptedTopic(String thingName) {
            return String.format("$aws/things/%s/shadow/get/accepted", thingName);
        }

        private static String rejectedTopic(String thingName) {
            return String.format("$aws/things/%s/shadow/get/rejected", thingName);
        }

        private static String updateDeltaTopic(String thingName) {
            return String.format("$aws/things/%s/shadow/update/delta", thingName);
        }

        private static String extractThingName(String topic) {
            String[] topicParts = topic.split("/");
            return topicParts[2];
        }
    }

    @Data
    @SuppressWarnings("PMD.LooseCoupling") // using HashMap to match iotshadow models
    static class Shadow {

        HashMap<String, Object> desired;
        HashMap<String, Object> reported;
        HashMap<String, Object> delta;
        int version;

        public Shadow(Shadow other) {
            if (other == null) {
                return;
            }
            this.desired = other.getDesired();
            this.reported = other.getReported();
            this.delta = other.getDelta();
            this.version = other.getVersion();
        }

        HashMap<String, Object> getDesired() {
            return desired == null ? null : new HashMap<>(desired);
        }

        HashMap<String, Object> getReported() {
            return reported == null ? null : new HashMap<>(reported);
        }

        HashMap<String, Object> getDelta() {
            return delta == null ? null : new HashMap<>(delta);
        }

        void recalculateDelta() {
            this.delta = calculateDelta(this);
        }

        private static HashMap<String, Object> calculateDelta(Shadow shadow) {
            // CIS shadow state is just {"version": "......"},
            // so we don't need a full-blown delta algo here
            if (shadow.getDesired() != null && shadow.getReported() != null
                    && Objects.equals(shadow.getDesired().get("version"), shadow.getReported().get("version"))) {
                return new HashMap<>();
            }
            if (shadow.getDesired() != null && shadow.getDesired().get("version") != null) {
                return shadow.getDesired();
            }
            return shadow.getReported();
        }
    }

    static class FakeConnectivityInfoCache extends ConnectivityInfoCache {

        private final Map<String, Set<HostAddress>> cache = new HashMap<>();

        @Override
        public void put(String source, Set<HostAddress> connectivityInfo) {
            cache.put(source, connectivityInfo);
        }

        @Override
        public Set<HostAddress> getAll() {
            return cache.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        }
    }

    static class FakeConnectivityInformation extends ConnectivityInformation {

        private final AtomicReference<List<ConnectivityInfo>> CONNECTIVITY_INFO_SAMPLE =
                new AtomicReference<>(Collections.singletonList(connectivityInfoWithRandomHost()));

        @Getter
        private int numConnectivityInfoChanges;
        private Set<HostAddress> prevAddresses;
        private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.RANDOM);
        private final AtomicBoolean failed = new AtomicBoolean();

        enum Mode {
            /**
             * Each call to getConnectivityInfo returns a unique, random response.
             */
            RANDOM,
            /**
             * Each call to getConnectivityInfo yields the same response.
             */
            CONSTANT,
            /**
             * Throw a runtime exception only the FIRST time getConnectivityInfo is called. Subsequent calls follow
             * {@link Mode#RANDOM} behavior.
             */
            FAIL_ONCE,
            /**
             * Simulate CIS throwing a validation exception.
             */
            THROW_VALIDATION_EXCEPTION
        }

        FakeConnectivityInformation() {
            super(null, null, new FakeConnectivityInfoCache());
        }

        void setMode(Mode mode) {
            this.mode.set(mode);
        }

        @Override
        public Optional<List<ConnectivityInfo>> getConnectivityInfo() {
            List<ConnectivityInfo> connectivityInfo = doGetConnectivityInfo();
            if (connectivityInfo != null) {
                Set<HostAddress> addresses = connectivityInfo.stream()
                        .map(HostAddress::of)
                        .collect(Collectors.toSet());
                getConnectivityInfoCache().put("source", addresses);
                Set<HostAddress> prevAddresses = this.prevAddresses;
                this.prevAddresses = addresses;
                if (!Objects.equals(addresses, prevAddresses)) {
                    numConnectivityInfoChanges++;
                }
            }
            return Optional.ofNullable(connectivityInfo);
        }

        private List<ConnectivityInfo> doGetConnectivityInfo() {
            switch (mode.get()) {
                case FAIL_ONCE:
                    if (!failed.getAndSet(true)) {
                        throw new RuntimeException("simulated getConnectivityInfo failure");
                    }
                    // fall-through to random behavior
                case RANDOM:
                    return Collections.singletonList(connectivityInfoWithRandomHost());
                case CONSTANT:
                    return CONNECTIVITY_INFO_SAMPLE.get();
                case THROW_VALIDATION_EXCEPTION:
                    // represents no info received
                    return null;
                default:
                    return Collections.emptyList();
            }
        }

        private static ConnectivityInfo connectivityInfoWithRandomHost() {
            return ConnectivityInfo.builder().hostAddress(Utils.generateRandomString(20)).build();
        }
    }
}
