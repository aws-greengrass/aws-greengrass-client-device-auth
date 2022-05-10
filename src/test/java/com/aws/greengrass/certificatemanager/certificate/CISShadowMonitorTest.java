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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
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
import software.amazon.awssdk.iot.ShadowStateFactory;
import software.amazon.awssdk.iot.Timestamp;
import software.amazon.awssdk.iot.iotshadow.IotShadowClient;
import software.amazon.awssdk.iot.iotshadow.model.ErrorResponse;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowRequest;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowResponse;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowSubscriptionRequest;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedEvent;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedSubscriptionRequest;
import software.amazon.awssdk.iot.iotshadow.model.ShadowState;
import software.amazon.awssdk.iot.iotshadow.model.ShadowStateWithDelta;
import software.amazon.awssdk.iot.iotshadow.model.ShadowUpdatedEvent;
import software.amazon.awssdk.iot.iotshadow.model.UpdateShadowRequest;
import software.amazon.awssdk.iot.iotshadow.model.UpdateShadowSubscriptionRequest;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    private static final String SHADOW_FIELD_VERSION = "version";
    private static final int SHADOW_FIELD_VERSION_INITIAL_VALUE = 0;
    private static final Map<String, Object> SHADOW_DESIRED_INITIAL_STATE =
            Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(SHADOW_FIELD_VERSION_INITIAL_VALUE));

    FakeIotShadowClient shadowClient = spy(new FakeIotShadowClient());

    InOrder shadowClientOrder = Mockito.inOrder(shadowClient);

    ExecutorService executor = TestUtils.synchronousExecutorService();

    FakeConnectivityInfoProvider connectivityInfoProvider = new FakeConnectivityInfoProvider();

    @Mock
    MqttClient mqttClient;

    @Mock
    CertificateGenerator certificateGenerator;

    CISShadowMonitor cisShadowMonitor;


    @BeforeEach
    void setup() throws Exception {
        cisShadowMonitor = new CISShadowMonitor(
                mqttClient,
                shadowClient.getConnection(),
                shadowClient,
                executor,
                SHADOW_NAME,
                connectivityInfoProvider
        );

        // pre-populate shadow
        UpdateShadowRequest updateShadowRequest = new UpdateShadowRequest();
        updateShadowRequest.thingName = SHADOW_NAME;
        updateShadowRequest.state = new ShadowState();
        updateShadowRequest.state.desired = new HashMap<>();
        updateShadowRequest.state.desired.putAll(SHADOW_DESIRED_INITIAL_STATE);
        shadowClient.PublishUpdateShadow(updateShadowRequest, QualityOfService.AT_LEAST_ONCE).get(5, TimeUnit.SECONDS);
        reset(shadowClient);
    }

    @AfterEach
    void tearDown() {
        cisShadowMonitor.stopMonitor();
    }

    // TODO adjust all tests to verify cert generation w/ respect to connectivity info

    @Test
    void GIVEN_CISShadowMonitor_WHEN_connection_resumed_THEN_get_shadow() {
        cisShadowMonitor.getCallbacks().onConnectionResumed(false);
        verifyPublishGetShadow();
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_start_monitor_THEN_subscribe_to_shadow_topics_and_get_shadow() throws InterruptedException {
        CountDownLatch shadowUpdated = whenUpdateShadowAccepted(1);

        cisShadowMonitor.startMonitor();
        verifyShadowDeltaUpdatedSubscription();
        verifyGetShadowAcceptedSubscription();
        verifyPublishGetShadow();

        assertTrue(shadowUpdated.await(5L, TimeUnit.SECONDS));
        assertDesiredAndReportedShadowState(
                Utils.immutableMap(SHADOW_FIELD_VERSION, "0"),
                Utils.immutableMap(SHADOW_FIELD_VERSION, "0"));
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_start_monitor_THEN_new_cert_generated() throws Exception {
        CountDownLatch shadowUpdated = whenUpdateShadowAccepted(1);

        cisShadowMonitor.addToMonitor(certificateGenerator);
        cisShadowMonitor.startMonitor();

        assertTrue(shadowUpdated.await(5L, TimeUnit.SECONDS));

        verify(certificateGenerator, times(1)).generateCertificate(any(), any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_THEN_new_cert_generated() throws Exception {
        int numShadowChanges = 1;
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(numShadowChanges);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        for (int i = 1; i <= numShadowChanges; i++) {
            Map<String, Object> desiredState = Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(i));
            publishDesiredShadowState(desiredState);
        }

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        assertDesiredAndReportedShadowState(
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)),
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)));

        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any(), any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_multiple_times_THEN_new_cert_generated_multiple_times() throws Exception {
        int numShadowChanges = 3;
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(numShadowChanges);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        for (int i = 1; i <= numShadowChanges; i++) {
            Map<String, Object> desiredState = Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(i));
            publishDesiredShadowState(desiredState);
        }

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        assertDesiredAndReportedShadowState(
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)),
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)));

        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any(), any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_duplicate_THEN_only_one_new_cert_generated() throws Exception {
        int numShadowChanges = 1;
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(numShadowChanges * 2);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        shadowClient.withDuplicatePublishing(true);

        for (int i = 1; i <= numShadowChanges; i++) {
            Map<String, Object> desiredState = Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(i));
            publishDesiredShadowState(desiredState);
        }

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        assertDesiredAndReportedShadowState(
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)),
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)));

        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any(), any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_multiple_times_with_duplicates_THEN_new_cert_generated_multiple_times() throws Exception {
        int numShadowChanges = 3;
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(numShadowChanges * 2);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        shadowClient.withDuplicatePublishing(true);

        for (int i = 1; i <= numShadowChanges; i++) {
            Map<String, Object> desiredState = Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(i));
            publishDesiredShadowState(desiredState);
        }

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        assertDesiredAndReportedShadowState(
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)),
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)));

        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any(), any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_and_shadow_update_failed_THEN_new_cert_generated(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, FakeIotShadowClient.SIMULATED_PUBLISH_EXCEPTION.getClass());

        int numShadowChanges = 1;
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(numShadowChanges);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        shadowClient.onPrePublish(topic -> {
            if (topic.endsWith("shadow/update/delta")) {
                shadowClient.withPublishException(true);
            }
        });

        for (int i = 1; i <= numShadowChanges; i++) {
            Map<String, Object> desiredState = Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(i));
            publishDesiredShadowState(desiredState);
        }

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        assertDesiredAndReportedShadowState(
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)),
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)));

        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any(), any());
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

        for (int i = 1; i <= numShadowChanges; i++) {
            shadowClient.withPublishException(false);
            Map<String, Object> desiredState = Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(i));
            publishDesiredShadowState(desiredState);
        }

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        assertDesiredAndReportedShadowState(
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)),
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)));

        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any(), any());
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

        for (int i = 1; i <= numShadowChanges; i++) {
            shadowClient.withPublishException(false);
            Map<String, Object> desiredState = Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(i));
            publishDesiredShadowState(desiredState);
        }

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        assertDesiredAndReportedShadowState(
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)),
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)));

        verify(certificateGenerator, times(numShadowChanges)).generateCertificate(any(), any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_twice_and_connectivity_call_fails_once_THEN_one_cert_generated(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, RuntimeException.class);

        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(2);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        // for first delta, simulate CIS failure
        connectivityInfoProvider.setMode(FakeConnectivityInfoProvider.Mode.FAIL);
        publishDesiredShadowState(Utils.immutableMap(SHADOW_FIELD_VERSION, "1"));

        // for second delta, everything works smoothly
        connectivityInfoProvider.setMode(FakeConnectivityInfoProvider.Mode.RANDOM);
        publishDesiredShadowState(Utils.immutableMap(SHADOW_FIELD_VERSION, "2"));

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));
        assertDesiredAndReportedShadowState(
                Utils.immutableMap(SHADOW_FIELD_VERSION, "2"),
                Utils.immutableMap(SHADOW_FIELD_VERSION, "2"));

        verify(certificateGenerator, times(1)).generateCertificate(any(), any());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changed_twice_and_connectivity_info_does_not_change_THEN_delta_processing_is_deduped() throws Exception {
        int numShadowChanges = 2;
        CountDownLatch shadowDeltaUpdated = whenShadowDeltaUpdated(numShadowChanges);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        // simulate race condition where CIS service state changes
        // before we call getConnectivityInfo. so even though connectivity changes twice,
        // CISShadowMonitor only sees the second value
        connectivityInfoProvider.setMode(FakeConnectivityInfoProvider.Mode.CONSTANT);

        for (int i = 1; i <= numShadowChanges; i++) {
            Map<String, Object> desiredState = Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(i));
            publishDesiredShadowState(desiredState);
        }

        assertTrue(shadowDeltaUpdated.await(5L, TimeUnit.SECONDS));

        // even though new cert isn't generated for each shadow change in this case,
        // we still expect the shadow to be fully synced
        assertDesiredAndReportedShadowState(
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)),
                Utils.immutableMap(SHADOW_FIELD_VERSION, String.valueOf(numShadowChanges)));

        verify(certificateGenerator, times(connectivityInfoProvider.getNumUniqueConnectivityInfoResponses())).generateCertificate(any(), any());
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
        shadowClientOrder.verify(shadowClient, times(1)).SubscribeToGetShadowAccepted(request.capture(), eq(QualityOfService.AT_LEAST_ONCE), any(), any());
        assertEquals(SHADOW_NAME, request.getValue().thingName);
    }

    private void verifyShadowDeltaUpdatedSubscription() {
        ArgumentCaptor<ShadowDeltaUpdatedSubscriptionRequest> request = ArgumentCaptor.forClass(ShadowDeltaUpdatedSubscriptionRequest.class);
        shadowClientOrder.verify(shadowClient, times(1)).SubscribeToShadowDeltaUpdatedEvents(request.capture(), eq(QualityOfService.AT_LEAST_ONCE), any(), any());
        assertEquals(SHADOW_NAME, request.getValue().thingName);
    }

    private void verifyPublishGetShadow() {
        ArgumentCaptor<GetShadowRequest> request = ArgumentCaptor.forClass(GetShadowRequest.class);
        shadowClientOrder.verify(shadowClient, times(1)).PublishGetShadow(request.capture(), eq(QualityOfService.AT_LEAST_ONCE));
        assertEquals(SHADOW_NAME, request.getValue().thingName);
    }

    private void assertDesiredAndReportedShadowState(Map<String, Object> desired, Map<String, Object> reported) {
        ShadowState state = shadowClient.getShadow(SHADOW_NAME).state;
        assertEquals(desired, state.desired, "unexpected desired state");
        assertEquals(reported, state.reported, "unexpected reported state");
    }


    static class FakeIotShadowClient extends IotShadowClient {

        static final RuntimeException SIMULATED_PUBLISH_EXCEPTION = new RuntimeException("simulated publish error");
        private static final CompletableFuture<Integer> DUMMY_PACKET_ID = CompletableFuture.completedFuture(0);
        private static final int INITIAL_SHADOW_VERSION = 1;
        private static final Gson MAPPER = new GsonBuilder()
                .disableHtmlEscaping()
                .registerTypeAdapter(Timestamp.class, new Timestamp.Serializer())
                .registerTypeAdapter(Timestamp.class, new Timestamp.Deserializer())
                .registerTypeAdapterFactory(new ShadowStateFactory())
                .create();

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
            final int version;
            final ShadowState state;

            static Shadow copy(Shadow other) {
                ShadowBuilder builder = Shadow.builder();
                if (other.state != null) {
                    builder.state = new ShadowState();
                    if (other.state.desired != null) {
                        builder.state.desired = new HashMap<>(other.state.desired);
                    }
                    if (other.state.reported != null) {
                        builder.state.reported = new HashMap<>(other.state.reported);
                    }
                }
                return builder
                        .version(other.version)
                        .build();
            }

            public Map<String, Object> calculateDelta() {
                Map<String, Object> delta = new HashMap<>();

                if (state == null) {
                    return delta;
                }

                Map<String, Object> desired = state.desired;
                if (desired == null || desired.isEmpty()) {
                    return delta;
                }

                Map<String, Object> reported = state.reported;
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
            Shadow shadow = getShadow(request.thingName);
            if (shadow == null) {
                String rejected = String.format("$aws/things/%s/shadow/get/rejected", request.thingName);
                ErrorResponse response = new ErrorResponse();
                response.message = "no shadow found";
                publishMessage(rejected, response);
            } else {
                String accepted = String.format("$aws/things/%s/shadow/get/accepted", request.thingName);
                GetShadowResponse response = new GetShadowResponse();
                response.version = shadow.version;
                response.state = new ShadowStateWithDelta();
                response.state.desired = shadow.state.desired;
                response.state.reported = shadow.state.reported;
                response.state.delta = new HashMap<>(shadow.calculateDelta());
                publishMessage(accepted, response);
            }
        }

        @SuppressWarnings("PMD.PrematureDeclaration")
        private void handleUpdateShadowRequest(UpdateShadowRequest request) {
            final AtomicBoolean accepted = new AtomicBoolean();
            final AtomicBoolean versionConflict = new AtomicBoolean();

            // update internal shadow state based on the request.
            // if no shadow exists, create a new one
            Shadow computedShadow = shadowByThingName.compute(request.thingName, (thingName, existingShadow) -> {
                Shadow shadow = existingShadow == null ?
                        Shadow.builder()
                                .state(request.state)
                                .version(INITIAL_SHADOW_VERSION)
                                .build()
                        : existingShadow;

                // if version is provided, it must match the latest version
                // https://docs.aws.amazon.com/iot/latest/developerguide/device-shadow-data-flow.html#optimistic-locking
                if (request.version != null && request.version != shadow.version) {
                    versionConflict.set(true);
                    return existingShadow;
                }

                accepted.set(true);

                return applyRequestToShadow(shadow, request);
            });

            if (versionConflict.get()) {
                publishVersionConflictError(request.thingName);
                return;
            }

            if (accepted.get()) {
                publishShadowAccepted(request.thingName);
            }

            Map<String, Object> delta = computedShadow.calculateDelta();
            if (!delta.isEmpty()) {
                publishShadowDelta(delta, computedShadow.version, request.thingName);
            }
        }

        private Shadow applyRequestToShadow(Shadow shadow, UpdateShadowRequest request) {
            if (request.state.desired == null && request.state.reported == null) {
                return Shadow.copy(shadow);
            }

            Map<String, Object> desired = request.state.desired == null ? shadow.state.desired : request.state.desired;
            Map<String, Object> reported = request.state.reported == null ? shadow.state.reported : request.state.reported;

            ShadowState shadowState = new ShadowState();
            shadowState.reported = reported == null ? null : new HashMap<>(reported);
            shadowState.desired = desired == null ? null : new HashMap<>(desired);

            return Shadow.builder()
                    .state(shadowState)
                    .version(shadow.version + 1)
                    .build();
        }

        private void publishVersionConflictError(String thingName) {
            publishShadowUpdateRejected(thingName, 409, "Version Conflict");
        }

        private void publishShadowAccepted(String thingName) {
            String topic = String.format("$aws/things/%s/shadow/update/accepted", thingName);
            ShadowUpdatedEvent event = new ShadowUpdatedEvent();
            publishMessage(topic, event);
        }

        private void publishShadowUpdateRejected(String thingName, Integer code, String message) {
            String topic = String.format("$aws/things/%s/shadow/update/rejected", thingName);
            ErrorResponse resp = new ErrorResponse();
            resp.code = code;
            resp.message = message;
            publishMessage(topic, resp);
        }

        private void publishShadowDelta(Map<String, Object> delta, int version, String thingName) {
            String topic = String.format("$aws/things/%s/shadow/update/delta", thingName);
            ShadowDeltaUpdatedEvent event = new ShadowDeltaUpdatedEvent();
            event.state = new HashMap<>(delta);
            event.version = version;
            publishMessage(topic, event);
        }

        private static <T> Optional<T> readPayload(MqttMessage message, Class<T> clazz) {
            try {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                return Optional.of(MAPPER.fromJson(payload, clazz));
            } catch (JsonParseException e) {
                return Optional.empty();
            }
        }

        private <T> Optional<MqttMessage> wrapInMessage(String topic, T payload, boolean dup) {
            try {
                return Optional.of(new MqttMessage(
                        topic,
                        MAPPER.toJson(payload).getBytes(StandardCharsets.UTF_8),
                        QualityOfService.AT_LEAST_ONCE,
                        false,
                        dup)
                );
            } catch (JsonParseException e) {
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

        Shadow getShadow(String thingName) {
            return Shadow.copy(shadowByThingName.get(thingName));
        }
    }

    static class FakeConnectivityInfoProvider extends ConnectivityInfoProvider {

        private final AtomicReference<List<ConnectivityInfo>> CONNECTIVITY_INFO_SAMPLE = new AtomicReference<>(Collections.singletonList(connectivityInfoWithRandomHost()));

        private final Set<Integer> responseHashes = new CopyOnWriteArraySet<>();
        private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.RANDOM);

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
             * Throw a runtime exception during getConnectivityInfo.
             */
            FAIL
        }

        FakeConnectivityInfoProvider() {
            super(null, null);
        }

        void setMode(Mode mode) {
            responseHashes.clear();
            this.mode.set(mode);
        }

        /**
         * Get the number of unique responses to getConnectivityInfo provided by this fake.
         * This number resets whenever {@link FakeConnectivityInfoProvider#setMode(Mode)} is called.
         *
         * @return number of unique connectivity info responses generated by this fake
         */
        int getNumUniqueConnectivityInfoResponses() {
            return responseHashes.size();
        }

        @Override
        public List<ConnectivityInfo> getConnectivityInfo() {
            List<ConnectivityInfo> connectivityInfo = doGetConnectivityInfo();
            cachedHostAddresses = connectivityInfo.stream().map(ConnectivityInfo::hostAddress).distinct().collect(Collectors.toList());
            responseHashes.add(cachedHostAddresses.hashCode());
            return connectivityInfo;
        }

        private List<ConnectivityInfo> doGetConnectivityInfo() {
            switch (mode.get()) {
                case RANDOM:
                    return Collections.singletonList(connectivityInfoWithRandomHost());
                case CONSTANT:
                    return CONNECTIVITY_INFO_SAMPLE.get();
                case FAIL:
                    throw new RuntimeException("simulated getConnectivityInfo failure");
                default:
                    return Collections.emptyList();
            }
        }

        private static ConnectivityInfo connectivityInfoWithRandomHost() {
            return ConnectivityInfo.builder()
                    .hostAddress(Utils.generateRandomString(20))
                    .build();
        }
    }
}
