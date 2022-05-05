/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.cisclient.ConnectivityInfoProvider;
import com.aws.greengrass.mqttclient.MqttClient;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.TestUtils;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.iotshadow.IotShadowClient;
import software.amazon.awssdk.iot.iotshadow.model.ErrorResponse;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowRequest;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowResponse;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowSubscriptionRequest;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedEvent;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedSubscriptionRequest;
import software.amazon.awssdk.iot.iotshadow.model.ShadowState;
import software.amazon.awssdk.iot.iotshadow.model.ShadowUpdatedEvent;
import software.amazon.awssdk.iot.iotshadow.model.UpdateShadowRequest;
import software.amazon.awssdk.iot.iotshadow.model.UpdateShadowSubscriptionRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CISShadowMonitorTest {
    private static final String SHADOW_NAME = "testThing-gci";

    FakeIotShadowClient shadowClient = spy(new FakeIotShadowClient());

    InOrder shadowClientOrder = Mockito.inOrder(shadowClient);

    ExecutorService executor = TestUtils.synchronousExecutorService();

    @Mock
    MqttClient mqttClient;

    @Mock
    ConnectivityInfoProvider mockConnectivityInfoProvider;

    @Mock
    CertificateGenerator certificateGenerator;

    CISShadowMonitor cisShadowMonitor;

    @BeforeEach
    void setup() {
        cisShadowMonitor = new CISShadowMonitor(
                mqttClient,
                shadowClient.getConnection(),
                shadowClient,
                executor,
                SHADOW_NAME,
                mockConnectivityInfoProvider
        );

        // pre-populate shadow
        UpdateShadowRequest updateShadowRequest = new UpdateShadowRequest();
        updateShadowRequest.thingName = SHADOW_NAME;
        updateShadowRequest.state = new ShadowState();
        updateShadowRequest.state.desired = new HashMap<>();
        updateShadowRequest.state.desired.put("test", 1);
        shadowClient.PublishUpdateShadow(updateShadowRequest, QualityOfService.AT_LEAST_ONCE);
        reset(shadowClient);
    }

    @AfterEach
    void tearDown() {
        cisShadowMonitor.stopMonitor();
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_connection_resumed_THEN_get_shadow() {
        cisShadowMonitor.getCallbacks().onConnectionResumed(false);
        verifyPublishGetShadow();
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_start_monitor_THEN_subscribe_to_shadow_topics_and_get_shadow() {
        cisShadowMonitor.startMonitor();
        verifyShadowDeltaUpdatedSubscription();
        verifyGetShadowAcceptedSubscription();
        verifyPublishGetShadow();
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_start_monitor_THEN_new_cert_generated() throws Exception {
        CountDownLatch shadowUpdated = whenUpdateShadowAccepted(1);

        cisShadowMonitor.addToMonitor(certificateGenerator);
        cisShadowMonitor.startMonitor();

        assertTrue(shadowUpdated.await(5L, TimeUnit.SECONDS));

        verify(certificateGenerator).generateCertificate(any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_THEN_new_cert_generated() throws Exception {
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(1);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        publishDesiredShadowState(Utils.immutableMap("newState", 1));

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        verify(certificateGenerator).generateCertificate(any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_multiple_times_THEN_new_cert_generated_multiple_times() throws Exception {
        int numShadowChanges = 3;

        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(numShadowChanges);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        IntStream.range(0, numShadowChanges).forEach(i -> {
            publishDesiredShadowState(Utils.immutableMap("newState", i));
        });

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_duplicate_THEN_only_one_new_cert_generated() throws Exception {
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(2);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        shadowClient.withDuplicatePublishing(true);

        publishDesiredShadowState(Utils.immutableMap("newState", 1));

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        verify(certificateGenerator).generateCertificate(any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_multiple_times_with_duplicates_THEN_new_cert_generated_multiple_times() throws Exception {
        int numShadowChanges = 3;
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(numShadowChanges * 2);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        shadowClient.withDuplicatePublishing(true);

        IntStream.range(0, numShadowChanges).forEach(i -> {
            publishDesiredShadowState(Utils.immutableMap("newState", i));
        });

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_and_shadow_update_failed_THEN_new_cert_generated(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, FakeIotShadowClient.SIMULATED_PUBLISH_EXCEPTION.getClass());

        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(1);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        shadowClient.onPrePublish(topic -> {
            if (topic.endsWith("shadow/update/delta")) {
                shadowClient.withPublishException(true);
            }
        });

        publishDesiredShadowState(Utils.immutableMap("newState", 1));

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        verify(certificateGenerator).generateCertificate(any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_multiple_times_and_shadow_update_failed_THEN_new_cert_generated_multiple_times(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, FakeIotShadowClient.SIMULATED_PUBLISH_EXCEPTION.getClass());

        int numShadowChanges = 3;
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(numShadowChanges);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        shadowClient.onPrePublish(topic -> {
            if (topic.endsWith("shadow/update/delta")) {
                // force the update shadow call within cisShadowMonitor to fail
                shadowClient.withPublishException(true);
            }
        });

        IntStream.range(0, numShadowChanges).forEach(i -> {
            shadowClient.withPublishException(false);
            publishDesiredShadowState(Utils.immutableMap("newState", i));
        });

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_multiple_times_with_duplicates_and_shadow_update_failed_THEN_new_cert_generated_multiple_times(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, FakeIotShadowClient.SIMULATED_PUBLISH_EXCEPTION.getClass());

        int numShadowChanges = 3;
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(numShadowChanges * 2);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        shadowClient.withDuplicatePublishing(true);
        shadowClient.onPrePublish(topic -> {
            if (topic.endsWith("shadow/update/delta")) {
                // force the update shadow call within cisShadowMonitor to fail
                shadowClient.withPublishException(true);
            }
        });

        IntStream.range(0, numShadowChanges).forEach(i -> {
            shadowClient.withPublishException(false);
            publishDesiredShadowState(Utils.immutableMap("newState", i));
        });

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_stop_monitor_THEN_unsubscribe() {
        cisShadowMonitor.startMonitor();
        assertTrue(shadowClient.hasSubscriptions());
        cisShadowMonitor.stopMonitor();
        assertFalse(shadowClient.hasSubscriptions());
    }

    private CountDownLatch whenUpdateShadowAccepted(int times) {
        CountDownLatch shadowUpdated = new CountDownLatch(times);
        UpdateShadowSubscriptionRequest request = new UpdateShadowSubscriptionRequest();
        request.thingName = SHADOW_NAME;
        shadowClient.SubscribeToUpdateShadowAccepted(request, QualityOfService.AT_LEAST_ONCE, resp -> shadowUpdated.countDown());
        return shadowUpdated;
    }

    private CountDownLatch whenShadowDeltaUpdated(int times) {
        CountDownLatch shadowDeltaUpdated = new CountDownLatch(times);
        ShadowDeltaUpdatedSubscriptionRequest request = new ShadowDeltaUpdatedSubscriptionRequest();
        request.thingName = SHADOW_NAME;
        shadowClient.SubscribeToShadowDeltaUpdatedEvents(request, QualityOfService.AT_LEAST_ONCE, resp -> shadowDeltaUpdated.countDown());
        return shadowDeltaUpdated;
    }

    private void publishDesiredShadowState(Map<String, Object> desired) {
        UpdateShadowRequest updateShadowRequest = new UpdateShadowRequest();
        updateShadowRequest.thingName = SHADOW_NAME;
        updateShadowRequest.state = new ShadowState();
        updateShadowRequest.state.desired = new HashMap<>(desired);
        shadowClient.PublishUpdateShadow(updateShadowRequest, QualityOfService.AT_LEAST_ONCE);
    }

    private void verifyGetShadowAcceptedSubscription() {
        ArgumentCaptor<GetShadowSubscriptionRequest> request = ArgumentCaptor.forClass(GetShadowSubscriptionRequest.class);
        shadowClientOrder.verify(shadowClient).SubscribeToGetShadowAccepted(request.capture(), eq(QualityOfService.AT_LEAST_ONCE), any(), any());
        assertEquals(SHADOW_NAME, request.getValue().thingName);
    }

    private void verifyShadowDeltaUpdatedSubscription() {
        ArgumentCaptor<ShadowDeltaUpdatedSubscriptionRequest> request = ArgumentCaptor.forClass(ShadowDeltaUpdatedSubscriptionRequest.class);
        shadowClientOrder.verify(shadowClient).SubscribeToShadowDeltaUpdatedEvents(request.capture(), eq(QualityOfService.AT_LEAST_ONCE), any(), any());
        assertEquals(SHADOW_NAME, request.getValue().thingName);
    }

    private void verifyPublishGetShadow() {
        ArgumentCaptor<GetShadowRequest> request = ArgumentCaptor.forClass(GetShadowRequest.class);
        shadowClientOrder.verify(shadowClient).PublishGetShadow(request.capture(), eq(QualityOfService.AT_LEAST_ONCE));
        assertEquals(SHADOW_NAME, request.getValue().thingName);
    }


    static class FakeIotShadowClient extends IotShadowClient {

        static final RuntimeException SIMULATED_PUBLISH_EXCEPTION = new RuntimeException("simulated publish error");
        private static final CompletableFuture<Integer> DUMMY_PACKET_ID = CompletableFuture.completedFuture(0);
        private static final int INITIAL_SHADOW_VERSION = 1;
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final Map<String, List<Consumer<MqttMessage>>> subscriptionsByTopic = new ConcurrentHashMap<>();
        private final Map<String, Shadow> shadowByThingName = new ConcurrentHashMap<>();
        private final AtomicReference<Consumer<String>> onPrePublish = new AtomicReference<>();
        private final AtomicBoolean withDuplicatePublishing = new AtomicBoolean();
        private final AtomicBoolean withPublishException = new AtomicBoolean();

        @Getter(AccessLevel.PACKAGE)
        private final MqttClientConnection connection;

        @Data
        @Builder
        static class Shadow {
            int version;
            ShadowState state;
        }

        FakeIotShadowClient() {
            this(mock(MqttClientConnection.class));
        }

        private FakeIotShadowClient(MqttClientConnection connection) {
            super(connection);
            this.connection = connection;
            // store subscriptions in-memory on subscribe
            when(this.connection.subscribe(any(), any(), any())).thenAnswer(invocation ->
                    propagateException(() -> {
                        String topic = invocation.getArgument(0);
                        Consumer<MqttMessage> messageHandler = invocation.getArgument(2);
                        subscriptionsByTopic.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>())
                                .add(messageHandler);
                        return DUMMY_PACKET_ID;
                    })
            );
            // fire messages to subscriptions on publish
            when(this.connection.publish(any(), any(), anyBoolean())).thenAnswer(invocation ->
                    propagateException(() -> {
                        MqttMessage message = invocation.getArgument(0);
                        String topic = message.getTopic();

                        if (topic.endsWith("shadow/get")) {
                            readPayload(message, GetShadowRequest.class)
                                    .ifPresent(this::handleGetShadowRequest);
                        } else if (topic.endsWith("shadow/update")) {
                            readPayload(message, UpdateShadowRequest.class)
                                    .ifPresent(this::handleUpdateShadowRequest);
                        } else {
                            throw new UnsupportedOperationException("please add a new handler for " + topic);
                        }
                        return DUMMY_PACKET_ID;
                    })
            );
            // delete subscriptions on unsubscribe
            when(this.connection.unsubscribe(any())).thenAnswer(invocation ->
                    propagateException(() -> {
                        String topic = invocation.getArgument(0);
                        subscriptionsByTopic.remove(topic);
                        return DUMMY_PACKET_ID;
                    })
            );
        }

        private void handleGetShadowRequest(GetShadowRequest request) {
            Shadow shadow = shadowByThingName.get(request.thingName);
            if (shadow == null) {
                String rejected = String.format("$aws/things/%s/shadow/get/rejected", request.thingName);
                ErrorResponse response = new ErrorResponse();
                response.message = "no shadow found";
                publishMessage(rejected, response);
            } else {
                String accepted = String.format("$aws/things/%s/shadow/get/accepted", request.thingName);
                GetShadowResponse response = new GetShadowResponse();
                response.version = shadow.version;
                publishMessage(accepted, response);
            }
        }

        private void handleUpdateShadowRequest(UpdateShadowRequest request) {
            // create a new shadow if one doesn't exist already
            Shadow shadow = shadowByThingName.compute(request.thingName, (k, v) -> {
                if (v != null) {
                    return v;
                }
                return Shadow.builder()
                        .state(request.state)
                        .version(INITIAL_SHADOW_VERSION)
                        .build();
            });

            if (shadow.version == INITIAL_SHADOW_VERSION || request.version == null || request.version == shadow.version) {
                publishShadowAccepted(request.thingName);

                if (request.state.desired == null && request.state.reported == null) {
                    return;
                }

                Map<String, Object> desired = request.state.desired == null ? shadow.state.desired : request.state.desired;
                Map<String, Object> reported = request.state.reported == null ? shadow.state.reported : request.state.reported;

                Map<String, Object> delta = getDelta(desired, reported);
                if (delta.isEmpty()) {
                    return;
                }

                shadow.state.desired = desired == null ? null : new HashMap<>(desired);
                shadow.state.reported = reported == null ? null : new HashMap<>(reported);
                shadow.version += 1;
                publishShadowDelta(delta, request.thingName);
            }
        }

        private void publishShadowAccepted(String thingName) {
            String topic = String.format("$aws/things/%s/shadow/update/accepted", thingName);
            ShadowUpdatedEvent event = new ShadowUpdatedEvent();
            publishMessage(topic, event);
        }

        private void publishShadowDelta(Map<String, Object> delta, String thingName) {
            String topic = String.format("$aws/things/%s/shadow/update/delta", thingName);
            ShadowDeltaUpdatedEvent event = new ShadowDeltaUpdatedEvent();
            event.state = new HashMap<>(delta);
            event.version = shadowByThingName.get(thingName).version;
            publishMessage(topic, event);
        }

        private Map<String, Object> getDelta(Map<String, Object> desired, Map<String, Object> reported) {
            Map<String, Object> delta = new HashMap<>();

            if (desired == null || desired.isEmpty()) {
                return delta;
            }

            if (reported == null || reported.isEmpty()) {
                delta.putAll(desired);
                return delta;
            }

            for (Map.Entry<String, Object> entry : desired.entrySet()) {
                if (reported.containsKey(entry.getKey())) {
                    if (!Objects.equals(reported.get(entry.getKey()), entry.getValue())) {
                        delta.put(entry.getKey(), entry.getValue());
                    }
                } else {
                    delta.put(entry.getKey(), entry.getValue());
                }
            }
            return delta;
        }

        private static <T> Optional<T> readPayload(MqttMessage message, Class<T> clazz) {
            try {
                return Optional.of(MAPPER.readValue(message.getPayload(), clazz));
            } catch (IOException e) {
                return Optional.empty();
            }
        }

        private <T> Optional<MqttMessage> wrapInMessage(String topic, T payload, boolean dup) {
            try {
                return Optional.of(new MqttMessage(
                        topic,
                        MAPPER.writeValueAsBytes(payload),
                        QualityOfService.AT_LEAST_ONCE,
                        false,
                        dup)
                );
            } catch (JsonProcessingException e) {
                return Optional.empty();
            }
        }

        private <T> void publishMessage(String topic, T event) {
            if (withPublishException.get()) {
                throw SIMULATED_PUBLISH_EXCEPTION;
            }

            Consumer<String> onPrePublish = this.onPrePublish.get();
            if (onPrePublish != null) {
                onPrePublish.accept(topic);
            }

            List<Consumer<MqttMessage>> subscribers = subscriptionsByTopic.get(topic);
            if (subscribers != null) {
                subscribers.forEach(subscriber ->
                        wrapInMessage(topic, event, false).ifPresent(subscriber));
                if (withDuplicatePublishing.get()) {
                    subscribers.forEach(subscriber ->
                            wrapInMessage(topic, event, true).ifPresent(subscriber));
                }
            }
        }

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private CompletableFuture<Integer> propagateException(Callable<CompletableFuture<Integer>> action) {
            try {
                return action.call();
            } catch (Exception e) {
                CompletableFuture<Integer> cf = new CompletableFuture<>();
                cf.completeExceptionally(e);
                return cf;
            }
        }

        void onPrePublish(Consumer<String> action) {
            onPrePublish.set(action);
        }

        void withPublishException(boolean publishException) {
            withPublishException.set(publishException);
        }

        void withDuplicatePublishing(boolean duplicatePublishing) {
            withDuplicatePublishing.set(duplicatePublishing);
        }

        boolean hasSubscriptions() {
            return !subscriptionsByTopic.isEmpty();
        }
    }
}
