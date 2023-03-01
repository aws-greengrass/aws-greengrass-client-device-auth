/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.TestUtils;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.iotshadow.IotShadowClient;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowRequest;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowResponse;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedEvent;
import software.amazon.awssdk.iot.iotshadow.model.ShadowStateWithDelta;
import software.amazon.awssdk.iot.iotshadow.model.UpdateShadowRequest;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CISShadowMonitorTest {

    private static final String SHADOW_NAME = "testThing-gci";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CompletableFuture<Integer> DUMMY_PACKET_ID = CompletableFuture.completedFuture(0);
    private static final String SHADOW_ACCEPTED_TOPIC =
            String.format("$aws/things/%s/shadow/get/accepted", SHADOW_NAME);
    private static final String SHADOW_DELTA_UPDATED_TOPIC =
            String.format("$aws/things/%s/shadow/update/delta", SHADOW_NAME);
    private static final String GET_SHADOW_TOPIC = String.format("$aws/things/%s/shadow/get", SHADOW_NAME);
    private static final String UPDATE_SHADOW_TOPIC = String.format("$aws/things/%s/shadow/update", SHADOW_NAME);

    private final FakeIotShadowClient shadowClient = spy(new FakeIotShadowClient());
    private final MqttClientConnection shadowClientConnection = shadowClient.getConnection();
    private final ExecutorService executor = TestUtils.synchronousExecutorService();
    private final FakeConnectivityInformation connectivityInfoProvider = new FakeConnectivityInformation();

    @Mock
    CertificateGenerator certificateGenerator;

    CISShadowMonitor cisShadowMonitor;

    @BeforeEach
    void setup() {
        cisShadowMonitor = new CISShadowMonitor(shadowClientConnection, shadowClient, executor, SHADOW_NAME,
                connectivityInfoProvider);
    }

    @AfterEach
    void tearDown() {
        cisShadowMonitor.stopMonitor();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_start_monitor_OR_reconnect_THEN_get_shadow_is_processed() throws Exception {
        // make connectivity call yield the same response each time.
        // so for this scenario, the monitor starts up (we process get shadow response) and we
        // simulate a reconnection (process another get shadow response);
        // no extra rotations should occur.
        connectivityInfoProvider.setMode(FakeConnectivityInformation.Mode.CONSTANT);

        int shadowInitialVersion = 1;
        Map<String, Object> shadowInitialDesiredState = Utils.immutableMap("version", "value");
        Map<String, Object> shadowInitialReportedState = Collections.emptyMap();
        Map<String, Object> shadowInitialDelta = Utils.immutableMap("version", "value");

        // capture the subscription callback for get shadow operation.
        ArgumentCaptor<Consumer<MqttMessage>> getShadowCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_ACCEPTED_TOPIC), any(),
                getShadowCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // when get shadow request is published, send a response to the subscription callback
        when(shadowClientConnection.publish(argThat(new GetShadowRequestMatcher()), any(), anyBoolean())).thenAnswer(
                invocation -> {
                    GetShadowResponse response = new GetShadowResponse();
                    response.version = shadowInitialVersion;
                    response.state = new ShadowStateWithDelta();
                    response.state.desired = new HashMap<>(shadowInitialDesiredState);
                    response.state.reported = new HashMap<>(shadowInitialReportedState);
                    response.state.delta = new HashMap<>(shadowInitialDelta);

                    wrapInMessage(SHADOW_ACCEPTED_TOPIC, response, false).ifPresent(
                            resp -> getShadowCallback.getValue().accept(resp));

                    return DUMMY_PACKET_ID;
                });

        // notify when shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(shadowInitialDesiredState) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean())).thenAnswer(
                whenUpdateIsPublished);

        cisShadowMonitor.addToMonitor(certificateGenerator);
        cisShadowMonitor.startMonitor();

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));

        // simulate a reconnect
        cisShadowMonitor.accept(NetworkStateProvider.ConnectionState.NETWORK_UP);

        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changes_THEN_delta_is_processed() throws Exception {

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(),
                shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // generated list of deltas to feed to the shadow monitor
        List<Map<String, Object>> deltas =
                IntStream.range(0, 5).mapToObj(i -> Utils.immutableMap("version", (Object) String.valueOf(i)))
                        .collect(Collectors.toList());
        Map<String, Object> lastDelta = deltas.get(deltas.size() - 1);

        // notify when last shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(lastDelta) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean())).thenAnswer(
                whenUpdateIsPublished);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        // trigger update delta subscription callbacks
        AtomicInteger version = new AtomicInteger(1);
        deltas.forEach(delta -> {
            ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
            deltaUpdatedEvent.version = version.getAndIncrement();
            deltaUpdatedEvent.state = new HashMap<>(delta);
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, false).ifPresent(
                    resp -> shadowDeltaUpdatedCallback.getValue().accept(resp));
        });

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));
        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changes_with_duplicate_delta_messages_THEN_delta_processing_is_deduped()
            throws Exception {

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(),
                shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // generated list of deltas to feed to the shadow monitor
        List<Map<String, Object>> deltas =
                IntStream.range(0, 5).mapToObj(i -> Utils.immutableMap("version", (Object) String.valueOf(i)))
                        .collect(Collectors.toList());
        Map<String, Object> lastDelta = deltas.get(deltas.size() - 1);

        // notify when last shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(lastDelta) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean())).thenAnswer(
                whenUpdateIsPublished);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        // trigger update delta subscription callbacks
        AtomicInteger version = new AtomicInteger(1);
        deltas.forEach(delta -> {
            ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
            deltaUpdatedEvent.version = version.getAndIncrement();
            deltaUpdatedEvent.state = new HashMap<>(delta);

            // original message
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, false).ifPresent(
                    resp -> shadowDeltaUpdatedCallback.getValue().accept(resp));
            // duplicate message
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, true).ifPresent(
                    resp -> shadowDeltaUpdatedCallback.getValue().accept(resp));
        });

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));
        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings({"unchecked", "PMD.AvoidCatchingGenericException"})
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changes_AND_shadow_update_request_fails_THEN_delta_processing_is_unaffected(
            ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, RuntimeException.class);
        ignoreExceptionOfType(context, CompletionException.class);

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(),
                shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // generated list of deltas to feed to the shadow monitor
        List<Map<String, Object>> deltas =
                IntStream.range(0, 5).mapToObj(i -> Utils.immutableMap("version", (Object) String.valueOf(i)))
                        .collect(Collectors.toList());
        Map<String, Object> lastDelta = deltas.get(deltas.size() - 1);

        // notify when last shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(lastDelta) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean()))
                // first time, fail with direct exception
                .thenThrow(RuntimeException.class)
                // second time, fail with exception nested in the future
                .thenAnswer(invocation -> {
                    CompletableFuture<Integer> error = new CompletableFuture<>();
                    error.completeExceptionally(new RuntimeException());
                    return error;
                })
                // rest of the time, complete normally
                .thenAnswer(whenUpdateIsPublished);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        // trigger update delta subscription callbacks
        int version = 1;
        for (Map<String, Object> delta : deltas) {
            ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
            deltaUpdatedEvent.version = version++;
            deltaUpdatedEvent.state = new HashMap<>(delta);

            try {
                wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, false).ifPresent(
                        resp -> shadowDeltaUpdatedCallback.getValue().accept(resp));
            } catch (RuntimeException e) {
                if (version == 1) {
                    // expected exception on first publish
                    continue;
                }
                throw e;
            }
        }

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));
        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changes_AND_connectivity_call_fails_THEN_delta_not_processed_on_failure(
            ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, RuntimeException.class);

        // make connectivity call fail the first time
        connectivityInfoProvider.setMode(FakeConnectivityInformation.Mode.FAIL_ONCE);

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(),
                shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // generated list of deltas to feed to the shadow monitor
        List<Map<String, Object>> deltas =
                IntStream.range(0, 5).mapToObj(i -> Utils.immutableMap("version", (Object) String.valueOf(i)))
                        .collect(Collectors.toList());
        Map<String, Object> lastDelta = deltas.get(deltas.size() - 1);

        // notify when last shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(lastDelta) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean())).thenAnswer(
                whenUpdateIsPublished);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        // trigger update delta subscription callbacks
        AtomicInteger version = new AtomicInteger(1);
        deltas.forEach(delta -> {
            ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
            deltaUpdatedEvent.version = version.getAndIncrement();
            deltaUpdatedEvent.state = new HashMap<>(delta);

            // original message
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, false).ifPresent(
                    resp -> shadowDeltaUpdatedCallback.getValue().accept(resp));

            // duplicate message
            // opted to throw this scenario in rather than split it out into its own test.
            // we already have a specific test for duplicate messages, so no need for extra clutter.
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, true).ifPresent(
                    resp -> shadowDeltaUpdatedCallback.getValue().accept(resp));
        });

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));
        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_delta_duplicate_received_THEN_delta_processing_is_deduped(
            ExtensionContext context) throws Exception {
        // make connectivity call yield the same response each time,
        // to match scenario where we receive same shadow delta version multiple times.
        connectivityInfoProvider.setMode(FakeConnectivityInformation.Mode.CONSTANT);

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(),
                shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        Map<String, Object> delta = Utils.immutableMap("version", "1");

        // notify when last shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished =
                WhenUpdateIsPublished.builder().expectedReportedState(delta) // reported state updated to desired state
                        .expectedDesiredState(null) // desired state isn't modified
                        .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean())).thenAnswer(
                whenUpdateIsPublished);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        // trigger update delta subscription callbacks
        AtomicInteger version = new AtomicInteger(1);

        ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
        deltaUpdatedEvent.version = version.getAndIncrement();
        deltaUpdatedEvent.state = new HashMap<>(delta);


        // send the same message multiple times
        for (int i = 0; i < 2; i++) {
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, i > 0).ifPresent(
                    resp -> shadowDeltaUpdatedCallback.getValue().accept(resp));
        }

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));
        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_stop_monitor_THEN_unsubscribe() {
        AtomicInteger numSubscriptions = new AtomicInteger();
        when(shadowClientConnection.subscribe(any(), any(), any())).thenAnswer(invocation -> {
            numSubscriptions.incrementAndGet();
            return DUMMY_PACKET_ID;
        });
        when(shadowClientConnection.unsubscribe(any())).thenAnswer(invocation -> {
            numSubscriptions.decrementAndGet();
            return DUMMY_PACKET_ID;
        });


        cisShadowMonitor.startMonitor();
        assertTrue(numSubscriptions.get() > 0);

        cisShadowMonitor.stopMonitor();
        assertEquals(0, numSubscriptions.get());
    }

    /**
     * Verify that certificates are rotated only when connectivity info changes.
     *
     * @throws CertificateGenerationException n/a
     */
    private void verifyCertsRotatedWhenConnectivityChanges() throws CertificateGenerationException {
        verify(certificateGenerator,
                times(connectivityInfoProvider.getNumUniqueConnectivityInfoResponses())).generateCertificate(any(),
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

    private static <T> Optional<MqttMessage> wrapInMessage(String topic, T payload, boolean dup) {
        try {
            return Optional.of(
                    new MqttMessage(topic, MAPPER.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8),
                            QualityOfService.AT_LEAST_ONCE, false, dup));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    static class GetShadowRequestMatcher implements ArgumentMatcher<MqttMessage> {
        @Override
        public boolean matches(MqttMessage message) {
            if (message == null) {
                return false;
            }
            GetShadowRequest request = readValue(message, GetShadowRequest.class);
            return Objects.equals(message.getTopic(), GET_SHADOW_TOPIC) && Objects.equals(SHADOW_NAME,
                    request.thingName);
        }
    }

    @Builder
    static class ShadowUpdateRequestMatcher implements ArgumentMatcher<MqttMessage> {
        @Override
        public boolean matches(MqttMessage message) {
            if (message == null) {
                return false;
            }
            UpdateShadowRequest request = readValue(message, UpdateShadowRequest.class);
            return Objects.equals(message.getTopic(), UPDATE_SHADOW_TOPIC) && Objects.equals(SHADOW_NAME,
                    request.thingName);
        }
    }

    @Builder
    static class WhenUpdateIsPublished implements Answer<CompletableFuture<Integer>> {

        @Getter(AccessLevel.PACKAGE)
        private final CountDownLatch latch = new CountDownLatch(1);
        private final Map<String, Object> expectedReportedState;
        private final Map<String, Object> expectedDesiredState;

        @Override
        public CompletableFuture<Integer> answer(InvocationOnMock invocation) {
            MqttMessage message = invocation.getArgument(0);
            if (message == null || !message.getTopic().equals(UPDATE_SHADOW_TOPIC)) {
                return DUMMY_PACKET_ID;
            }

            UpdateShadowRequest request = readValue(message, UpdateShadowRequest.class);
            if (Objects.equals(request.state.reported, expectedReportedState) && Objects.equals(request.state.desired,
                    expectedDesiredState)) {
                latch.countDown();
            }
            return DUMMY_PACKET_ID;
        }
    }

    static class FakeIotShadowClient extends IotShadowClient {

        @Getter(AccessLevel.PACKAGE)
        private final MqttClientConnection connection;

        FakeIotShadowClient() {
            this(mock(MqttClientConnection.class));
        }

        private FakeIotShadowClient(MqttClientConnection connection) {
            super(connection);
            this.connection = connection;
            when(this.connection.subscribe(any(), any(), any())).thenReturn(DUMMY_PACKET_ID);
            when(this.connection.publish(any(), any(), anyBoolean())).thenReturn(DUMMY_PACKET_ID);
            when(this.connection.unsubscribe(any())).thenReturn(DUMMY_PACKET_ID);
        }
    }


    static class FakeConnectivityInformation extends ConnectivityInformation {

        private final AtomicReference<List<ConnectivityInfo>> CONNECTIVITY_INFO_SAMPLE =
                new AtomicReference<>(Collections.singletonList(connectivityInfoWithRandomHost()));
        private final Set<Integer> responseHashes = new CopyOnWriteArraySet<>();
        private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.RANDOM);
        private final AtomicBoolean failed = new AtomicBoolean();
        private List<String> cachedHostAddresses;

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
            FAIL_ONCE
        }

        FakeConnectivityInformation() {
            super(null, null, null);
        }

        void setMode(Mode mode) {
            this.mode.set(mode);
        }

        /**
         * Get the number of unique responses to getConnectivityInfo provided by this fake.
         *
         * @return number of unique connectivity info responses generated by this fake
         */
        int getNumUniqueConnectivityInfoResponses() {
            return responseHashes.size();
        }

        @Override
        public List<ConnectivityInfo> getConnectivityInfo() {
            List<ConnectivityInfo> connectivityInfo = doGetConnectivityInfo();
            cachedHostAddresses = connectivityInfo.stream().map(ConnectivityInfo::hostAddress).distinct()
                    .collect(Collectors.toList());
            responseHashes.add(cachedHostAddresses.hashCode());
            return connectivityInfo;
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
                default:
                    return Collections.emptyList();
            }
        }

        private static ConnectivityInfo connectivityInfoWithRandomHost() {
            return ConnectivityInfo.builder().hostAddress(Utils.generateRandomString(20)).build();
        }
    }
}
