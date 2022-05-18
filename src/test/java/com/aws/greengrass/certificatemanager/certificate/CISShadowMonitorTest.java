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
import software.amazon.awssdk.crt.mqtt.MqttException;
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
import java.security.KeyStoreException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CISShadowMonitorTest {

    private static final String SHADOW_NAME = "testThing-gci";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CompletableFuture<Integer> DUMMY_PACKET_ID = CompletableFuture.completedFuture(0);
    private static final String SHADOW_ACCEPTED_TOPIC = String.format("$aws/things/%s/shadow/get/accepted", SHADOW_NAME);
    private static final String SHADOW_DELTA_UPDATED_TOPIC = String.format("$aws/things/%s/shadow/update/delta", SHADOW_NAME);
    private static final String GET_SHADOW_TOPIC = String.format("$aws/things/%s/shadow/get", SHADOW_NAME);
    private static final String UPDATE_SHADOW_TOPIC = String.format("$aws/things/%s/shadow/update", SHADOW_NAME);


    FakeIotShadowClient shadowClient = spy(new FakeIotShadowClient());
    MqttClientConnection shadowClientConnection = shadowClient.getConnection();
    ExecutorService executor = TestUtils.synchronousExecutorService();
    ScheduledExecutorService ses = new DelegatedScheduledExecutorService(executor);
    FakeConnectivityInfoProvider connectivityInfoProvider = new FakeConnectivityInfoProvider();

    @Mock
    MqttClient mqttClient;

    @Mock
    CertificateGenerator certificateGenerator;

    CISShadowMonitor cisShadowMonitor;

    @BeforeEach
    void setup() {
        cisShadowMonitor = new CISShadowMonitor(
                mqttClient,
                shadowClientConnection,
                shadowClient,
                ses,
                executor,
                SHADOW_NAME,
                connectivityInfoProvider
        );
        cisShadowMonitor.setShadowProcessingDelayMs(1L);
    }

    /**
     * Use only in special-cases where test relies on multithreading.
     */
    private void multiThreadedSetup() {
        tearDown();
        executor = Executors.newCachedThreadPool();
        ses = new DelegatedScheduledExecutorService(executor);
        setup();
    }

    @AfterEach
    void tearDown() {
        cisShadowMonitor.stopMonitor();
        ses.shutdownNow();
        executor.shutdownNow();
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_interrupted_during_subscription_THEN_startup_gracefully_stops() throws Exception {
        // test needs subscriptions to run in a separate thread,
        // so we can properly interrupt them
        multiThreadedSetup();

        when(shadowClientConnection.subscribe(eq(SHADOW_ACCEPTED_TOPIC), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(5000L);
            return DUMMY_PACKET_ID;
        });

        CountDownLatch subscribedToDeltaTopic = new CountDownLatch(1);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(), any())).thenAnswer(invocation -> {
            subscribedToDeltaTopic.countDown();
            return DUMMY_PACKET_ID;
        });

        cisShadowMonitor.startMonitor();
        assertTrue(subscribedToDeltaTopic.await(5L, TimeUnit.SECONDS));
        // by now, get shadow subscription is executing, interrupt it
        cisShadowMonitor.stopMonitor();

        // if we interrupted properly, the get shadow request will never happen
        verify(shadowClientConnection, never()).publish(any(), any(), anyBoolean());
    }

    @Test
    void GIVEN_CISShadowMonitor_WHEN_exception_during_subscription_THEN_subscription_retries(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, ExecutionException.class);

        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(), any()))
                .thenAnswer(invocation -> {
                    CompletableFuture<Integer> error = new CompletableFuture<>();
                    error.completeExceptionally(new MqttException(""));
                    return error;
                })
                .thenReturn(DUMMY_PACKET_ID);

        cisShadowMonitor.setSubscribeInitialRetryInterval(Duration.ofMillis(1L));

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.getSubscribeTaskFuture().get(5L, TimeUnit.MINUTES);

        verify(shadowClientConnection).subscribe(eq(SHADOW_ACCEPTED_TOPIC), any(), any());
        // once with error and second time with success
        verify(shadowClientConnection, times(2)).subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_start_monitor_OR_reconnect_THEN_get_shadow_is_processed() throws Exception {

        int shadowInitialVersion = 1;
        Map<String, Object> shadowInitialDesiredState = Utils.immutableMap("field", "value");
        Map<String, Object> shadowInitialReportedState = Collections.emptyMap();
        Map<String, Object> shadowInitialDelta = Utils.immutableMap("field", "value");

        // capture the subscription callback for get shadow operation.
        ArgumentCaptor<Consumer<MqttMessage>> getShadowCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_ACCEPTED_TOPIC), any(), getShadowCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // when get shadow request is published, send a response to the subscription callback
        when(shadowClientConnection.publish(argThat(new GetShadowRequestMatcher()), any(), anyBoolean()))
                .thenAnswer(invocation -> {
                    GetShadowResponse response = new GetShadowResponse();
                    response.version = shadowInitialVersion;
                    response.state = new ShadowStateWithDelta();
                    response.state.desired = new HashMap<>(shadowInitialDesiredState);
                    response.state.reported = new HashMap<>(shadowInitialReportedState);
                    response.state.delta = new HashMap<>(shadowInitialDelta);

                    wrapInMessage(SHADOW_ACCEPTED_TOPIC, response, false).ifPresent(resp ->
                            getShadowCallback.getValue().accept(resp));

                    return DUMMY_PACKET_ID;
                });

        // notify when shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(shadowInitialDesiredState) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean()))
                .thenAnswer(whenUpdateIsPublished);

        cisShadowMonitor.addToMonitor(certificateGenerator);
        cisShadowMonitor.startMonitor();

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));

        // simulate a reconnect
        cisShadowMonitor.getCallbacks().onConnectionResumed(false);

        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changes_THEN_delta_is_processed() throws Exception {

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(), shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // generated list of deltas to feed to the shadow monitor
        List<Map<String, Object>> deltas = IntStream.range(0, 5)
                .mapToObj(i -> Utils.immutableMap("field", (Object) String.valueOf(i)))
                .collect(Collectors.toList());
        Map<String, Object> lastDelta = deltas.get(deltas.size() - 1);

        // notify when last shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(lastDelta) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean()))
                .thenAnswer(whenUpdateIsPublished);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        // trigger update delta subscription callbacks
        AtomicInteger version = new AtomicInteger(1);
        deltas.forEach(delta -> {
            ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
            deltaUpdatedEvent.version = version.getAndIncrement();
            deltaUpdatedEvent.state = new HashMap<>(delta);
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, false).ifPresent(resp ->
                    shadowDeltaUpdatedCallback.getValue().accept(resp));
        });

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));
        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changes_with_duplicate_delta_messages_THEN_delta_processing_is_deduped() throws Exception {

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(), shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // generated list of deltas to feed to the shadow monitor
        List<Map<String, Object>> deltas = IntStream.range(0, 5)
                .mapToObj(i -> Utils.immutableMap("field", (Object) String.valueOf(i)))
                .collect(Collectors.toList());
        Map<String, Object> lastDelta = deltas.get(deltas.size() - 1);

        // notify when last shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(lastDelta) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean()))
                .thenAnswer(whenUpdateIsPublished);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        // trigger update delta subscription callbacks
        AtomicInteger version = new AtomicInteger(1);
        deltas.forEach(delta -> {
            ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
            deltaUpdatedEvent.version = version.getAndIncrement();
            deltaUpdatedEvent.state = new HashMap<>(delta);

            // original message
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, false).ifPresent(resp ->
                    shadowDeltaUpdatedCallback.getValue().accept(resp));
            // duplicate message
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, true).ifPresent(resp ->
                    shadowDeltaUpdatedCallback.getValue().accept(resp));
        });

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));
        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changes_AND_shadow_update_request_fails_THEN_delta_processing_is_unaffected(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, RuntimeException.class);

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(), shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // generated list of deltas to feed to the shadow monitor
        List<Map<String, Object>> deltas = IntStream.range(0, 5)
                .mapToObj(i -> Utils.immutableMap("field", (Object) String.valueOf(i)))
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

        AtomicReference<CountDownLatch> requestProcessed = new AtomicReference<>(new CountDownLatch(1));
        cisShadowMonitor.setOnShadowProcessingWorkComplete(() -> requestProcessed.get().countDown());

        // trigger update delta subscription callbacks
        int version = 1;
        for (Map<String, Object> delta : deltas) {
            ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
            deltaUpdatedEvent.version = version++;
            deltaUpdatedEvent.state = new HashMap<>(delta);

            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, false).ifPresent(resp ->
                    shadowDeltaUpdatedCallback.getValue().accept(resp));

            // force requests to process one-at-at-time,
            // rather than being deduped at queue-time.
            assertTrue(requestProcessed.get().await(5L, TimeUnit.SECONDS));
            requestProcessed.set(new CountDownLatch(1)); // reset the latch
        }

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));
        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changes_AND_connectivity_call_fails_THEN_delta_not_processed_on_failure(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, RuntimeException.class);

        // make connectivity call fail the first time
        connectivityInfoProvider.setMode(FakeConnectivityInfoProvider.Mode.FAIL_ONCE);

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(), shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // generated list of deltas to feed to the shadow monitor
        List<Map<String, Object>> deltas = IntStream.range(0, 5)
                .mapToObj(i -> Utils.immutableMap("field", (Object) String.valueOf(i)))
                .collect(Collectors.toList());
        Map<String, Object> lastDelta = deltas.get(deltas.size() - 1);

        // notify when last shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(lastDelta) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean()))
                .thenAnswer(whenUpdateIsPublished);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        AtomicReference<CountDownLatch> requestProcessed = new AtomicReference<>(new CountDownLatch(1));
        cisShadowMonitor.setOnShadowProcessingWorkComplete(() -> requestProcessed.get().countDown());

        // trigger update delta subscription callbacks
        int version = 1;
        for (Map<String, Object> delta : deltas) {
            ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
            deltaUpdatedEvent.version = version++;
            deltaUpdatedEvent.state = new HashMap<>(delta);

            // original message
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, false).ifPresent(resp ->
                    shadowDeltaUpdatedCallback.getValue().accept(resp));

            // force requests to process one-at-at-time,
            // rather than being deduped at queue-time.
            assertTrue(requestProcessed.get().await(5L, TimeUnit.SECONDS));
            requestProcessed.set(new CountDownLatch(1)); // reset the latch
        }

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));
        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_changes_AND_connectivity_response_does_not_change_THEN_delta_processing_is_deduped() throws Exception {
        // simulate a race condition where CIS service state changes before we call getConnectivityInfo.
        // so even though connectivity changes twice, CISShadowMonitor only sees the second value:
        //
        //  1) CIS state changes to "A", delta "1" sent
        //  2) receive and start processing shadow delta "1"
        //  3) CIS state changes to "B", delta "2" sent
        //  4) connectivity response for delta "1":  "B"
        //  5) receive and start processing shadow delta "2"
        //  6) connectivity response for delta "2":  "B"
        connectivityInfoProvider.setMode(FakeConnectivityInfoProvider.Mode.CONSTANT);

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(), shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        // generated list of deltas to feed to the shadow monitor
        List<Map<String, Object>> deltas = IntStream.range(0, 2)
                .mapToObj(i -> Utils.immutableMap("field", (Object) String.valueOf(i)))
                .collect(Collectors.toList());
        Map<String, Object> lastDelta = deltas.get(deltas.size() - 1);

        // notify when last shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(lastDelta) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean()))
                .thenAnswer(whenUpdateIsPublished);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        AtomicReference<CountDownLatch> requestProcessed = new AtomicReference<>(new CountDownLatch(1));
        cisShadowMonitor.setOnShadowProcessingWorkComplete(() -> requestProcessed.get().countDown());

        // trigger update delta subscription callbacks
        AtomicInteger version = new AtomicInteger(1);
        for (Map<String, Object> delta : deltas) {
            ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
            deltaUpdatedEvent.version = version.getAndIncrement();
            deltaUpdatedEvent.state = new HashMap<>(delta);

            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, false).ifPresent(resp ->
                    shadowDeltaUpdatedCallback.getValue().accept(resp));

            // force requests to process one-at-at-time,
            // rather than being deduped at queue-time.
            assertTrue(requestProcessed.get().await(5L, TimeUnit.SECONDS));
            requestProcessed.set(new CountDownLatch(1)); // reset the latch
        }

        assertTrue(whenUpdateIsPublished.getLatch().await(5L, TimeUnit.SECONDS));
        verifyCertsRotatedWhenConnectivityChanges();
    }

    @Test
    @SuppressWarnings("unchecked")
    void GIVEN_CISShadowMonitor_WHEN_cis_shadow_delta_duplicate_received_THEN_delta_processing_is_deduped(ExtensionContext context) throws Exception {
        // make connectivity call yield the same response each time,
        // to match scenario where we receive same shadow delta version multiple times.
        connectivityInfoProvider.setMode(FakeConnectivityInfoProvider.Mode.CONSTANT);

        // capture the subscription callback for shadow delta update
        ArgumentCaptor<Consumer<MqttMessage>> shadowDeltaUpdatedCallback = ArgumentCaptor.forClass(Consumer.class);
        when(shadowClientConnection.subscribe(eq(SHADOW_DELTA_UPDATED_TOPIC), any(), shadowDeltaUpdatedCallback.capture())).thenReturn(DUMMY_PACKET_ID);

        Map<String, Object> delta = Utils.immutableMap("field", "1");

        // notify when last shadow update is published
        WhenUpdateIsPublished whenUpdateIsPublished = WhenUpdateIsPublished.builder()
                .expectedReportedState(delta) // reported state updated to desired state
                .expectedDesiredState(null) // desired state isn't modified
                .build();
        when(shadowClientConnection.publish(argThat(new ShadowUpdateRequestMatcher()), any(), anyBoolean()))
                .thenAnswer(whenUpdateIsPublished);

        cisShadowMonitor.startMonitor();
        cisShadowMonitor.addToMonitor(certificateGenerator);

        // trigger update delta subscription callbacks
        AtomicInteger version = new AtomicInteger(1);

        ShadowDeltaUpdatedEvent deltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
        deltaUpdatedEvent.version = version.getAndIncrement();
        deltaUpdatedEvent.state = new HashMap<>(delta);

        AtomicReference<CountDownLatch> requestProcessed = new AtomicReference<>(new CountDownLatch(1));
        cisShadowMonitor.setOnShadowProcessingWorkComplete(() -> requestProcessed.get().countDown());

        // send the same message multiple times
        for (int i = 0; i < 2; i++) {
            wrapInMessage(SHADOW_DELTA_UPDATED_TOPIC, deltaUpdatedEvent, i > 0).ifPresent(resp ->
                    shadowDeltaUpdatedCallback.getValue().accept(resp));

            // force requests to process one-at-at-time,
            // rather than being deduped at queue-time.
            assertTrue(requestProcessed.get().await(5L, TimeUnit.SECONDS));
            requestProcessed.set(new CountDownLatch(1)); // reset the latch
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
     * @throws KeyStoreException n/a
     */
    private void verifyCertsRotatedWhenConnectivityChanges() throws KeyStoreException {
        verify(certificateGenerator, times(connectivityInfoProvider.getNumUniqueConnectivityInfoResponses())).generateCertificate(any(), any());
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
            return Optional.of(new MqttMessage(
                    topic,
                    MAPPER.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8),
                    QualityOfService.AT_LEAST_ONCE,
                    false,
                    dup)
            );
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
            return Objects.equals(message.getTopic(), GET_SHADOW_TOPIC) &&
                    Objects.equals(SHADOW_NAME, request.thingName);
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
            return Objects.equals(message.getTopic(), UPDATE_SHADOW_TOPIC) &&
                    Objects.equals(SHADOW_NAME, request.thingName);
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
            if (Objects.equals(request.state.reported, expectedReportedState) &&
                    Objects.equals(request.state.desired, expectedDesiredState)) {
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

    static class FakeConnectivityInfoProvider extends ConnectivityInfoProvider {

        private final AtomicReference<List<ConnectivityInfo>> CONNECTIVITY_INFO_SAMPLE = new AtomicReference<>(Collections.singletonList(connectivityInfoWithRandomHost()));
        private final Set<Integer> responseHashes = new CopyOnWriteArraySet<>();
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
             * Throw a runtime exception only the FIRST time getConnectivityInfo is called.
             * Subsequent calls follow {@link Mode#RANDOM} behavior.
             */
            FAIL_ONCE
        }

        FakeConnectivityInfoProvider() {
            super(null, null);
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
            cachedHostAddresses = connectivityInfo.stream().map(ConnectivityInfo::hostAddress).distinct().collect(Collectors.toList());
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
            return ConnectivityInfo.builder()
                    .hostAddress(Utils.generateRandomString(20))
                    .build();
        }
    }

    /**
     * A ScheduledThreadPoolExecutor that executes using a provided ExecutorService.
     */
    static class DelegatedScheduledExecutorService extends ScheduledThreadPoolExecutor {

        private final ExecutorService executor;

        public DelegatedScheduledExecutorService(ExecutorService executor) {
            super(0);
            this.executor = executor;
        }

        @Override
        public void execute(Runnable command) {
            executor.execute(command);
        }

        @Override
        public void shutdown() {
            executor.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return executor.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return executor.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return executor.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return executor.awaitTermination(timeout, unit);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return executor.submit(task);
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return executor.submit(task, result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return executor.submit(task);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return executor.invokeAll(tasks);
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            return executor.invokeAll(tasks, timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            return executor.invokeAny(tasks);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return executor.invokeAny(tasks, timeout, unit);
        }
    }
}
