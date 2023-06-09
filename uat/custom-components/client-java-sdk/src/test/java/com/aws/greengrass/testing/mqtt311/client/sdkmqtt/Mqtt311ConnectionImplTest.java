/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt311.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.GRPCClient;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection.ConnAckInfo;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection.ConnectResult;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection.Message;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection.PubAckInfo;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection.SubAckInfo;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection.Subscription;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection.UnsubAckInfo;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Mqtt311ConnectionImplTest {
    private static final int DEFAULT_TIMEOUT_SEC = 10;
    private static final int SHORT_TIMEOUT_SEC = 1;
    private static final int RETAIN_HANDLING = 25;              // value doesn't matter
    private static final boolean RETAIN_AS_PUBLISHED = false;   // value doesn't matter

    @Mock
    private GRPCClient grpcClient;

    @Mock
    private MqttClientConnection connection;

    private Mqtt311ConnectionImpl mqttConnectionImpl;

    @BeforeEach
    void setup() {
        mqttConnectionImpl = new Mqtt311ConnectionImpl(grpcClient, connection);
    }


    @Test
    void GIVEN_connnect_successful_WHEN_start_THEN_positive_result() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int connectionId = 1974;
        final boolean sessionPresent = true;

        final CompletableFuture<Boolean> connectFuture = new CompletableFuture<>();
        when(connection.connect()).thenReturn(connectFuture);
        connectFuture.complete(sessionPresent);

        // WHEN
        ConnectResult connectResult = mqttConnectionImpl.start(timeoutSeconds, connectionId);

        // THEN
        verify(connection).connect();
        assertNotNull(connectResult);
        assertTrue(connectResult.isConnected());

        ConnAckInfo connAckInfo = connectResult.getConnAckInfo();
        assertNotNull(connAckInfo);
        assertEquals(sessionPresent, connAckInfo.getSessionPresent());

        assertNull(connectResult.getError());
    }

    @Test
    void GIVEN_ioexeption_when_connect_WHEN_start_THEN_exception() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int connectionId = 1975;

        final CompletableFuture<Boolean> connectFuture = new CompletableFuture<>();
        when(connection.connect()).thenReturn(connectFuture);
        connectFuture.completeExceptionally(new IOException("Test"));

        // WHEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.start(timeoutSeconds, connectionId);
        }, "Exception occurred during connect");

        // THEN
        verify(connection).connect();
    }

    @Test
    void GIVEN_timedout_WHEN_start_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final int connectionId = 1976;

        final CompletableFuture<Boolean> connectFuture = new CompletableFuture<>();
        when(connection.connect()).thenReturn(connectFuture);

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.start(timeoutSeconds, connectionId);
        }, "Exception occurred during connect");

        verify(connection).connect();
    }


    @Test
    void GIVEN_disconnect_successful_WHEN_disconnect_THEN_client_methods_are_called() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int reasonCode = 4;

        final CompletableFuture<Void> connectFuture = new CompletableFuture<>();
        when(connection.disconnect()).thenReturn(connectFuture);
        connectFuture.complete(null);

        // WHEN
        mqttConnectionImpl.disconnect(timeoutSeconds, reasonCode, null);

        // THEN
        verify(connection).disconnect();
        verify(connection).close();
    }

    @Test
    void GIVEN_timedout_WHEN_disconnect_THEN_exception() throws MqttException {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final int reasonCode = 4;

        final CompletableFuture<Void> connectFuture = new CompletableFuture<>();
        when(connection.disconnect()).thenReturn(connectFuture);

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.disconnect(timeoutSeconds, reasonCode, null);
        }, "Could not disconnect");

        verify(connection).disconnect();
        verify(connection).close();
    }

    @Test
    void GIVEN_publish_successful_WHEN_publish_THEN_positive_result() throws MqttException {
        // GIVEN
        final int packetId = 23;
        final int reasonCode = Mqtt311ConnectionImpl.REASON_CODE_SUCCESS;

        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int QoS = QualityOfService.AT_LEAST_ONCE.getValue();
        final boolean retain = true;
        final String topic = "test/topic";
        final byte[] payload = "test_payload".getBytes(StandardCharsets.UTF_8);


        final Message message = Message.builder()
                                    .qos(QoS)
                                    .retain(retain)
                                    .topic(topic)
                                    .payload(payload)
                                    .build();

        final CompletableFuture<Integer> publishFuture = new CompletableFuture<>();
        when(connection.publish(any(MqttMessage.class))).thenReturn(publishFuture);
        publishFuture.complete(packetId);

        // move to connected state
        mqttConnectionImpl.connectionEvents.onConnectionResumed(true);

        // WHEN
        PubAckInfo pubAckInfo = mqttConnectionImpl.publish(timeoutSeconds, message);

        // THEN
        // check result in pubAckInfo
        assertNotNull(pubAckInfo);
        assertEquals(reasonCode, pubAckInfo.getReasonCode());
        assertNull(pubAckInfo.getReasonString());

        // check calls of `connection`
        ArgumentCaptor<MqttMessage> argument = ArgumentCaptor.forClass(MqttMessage.class);
        verify(connection).publish(argument.capture());

        MqttMessage mqttMessage = argument.getValue();
        assertNotNull(mqttMessage);

        assertEquals(QoS, mqttMessage.getQos().getValue());
        assertEquals(retain, mqttMessage.getRetain());
        assertEquals(topic, mqttMessage.getTopic());
        assertArrayEquals(payload, mqttMessage.getPayload());
    }

    @Test
    void GIVEN_not_connected_state_WHEN_publish_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int QoS = QualityOfService.AT_LEAST_ONCE.getValue();
        final boolean retain = true;
        final String topic = "test/topic";
        final byte[] payload = "test_payload".getBytes(StandardCharsets.UTF_8);

        final Message message = Message.builder()
                                    .qos(QoS)
                                    .retain(retain)
                                    .topic(topic)
                                    .payload(payload)
                                    .build();

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.publish(timeoutSeconds, message);
        }, "MQTT client is not in connected state");
    }

    @Test
    void GIVEN_timedout_WHEN_publish_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final int QoS = QualityOfService.AT_LEAST_ONCE.getValue();
        final boolean retain = true;
        final String topic = "test/topic";
        final byte[] payload = "test_payload".getBytes(StandardCharsets.UTF_8);

        final Message message = Message.builder()
                                    .qos(QoS)
                                    .retain(retain)
                                    .topic(topic)
                                    .payload(payload)
                                    .build();

        final CompletableFuture<Integer> publishFuture = new CompletableFuture<>();
        when(connection.publish(any(MqttMessage.class))).thenReturn(publishFuture);

        // move to connected state
        mqttConnectionImpl.connectionEvents.onConnectionResumed(true);

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.publish(timeoutSeconds, message);
        }, "Could not publish message");

        verify(connection).publish(any(MqttMessage.class));
    }


    @Test
    void GIVEN_subscribe_successful_WHEN_subscribe_THEN_positive_result() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final Integer subscriptionId = null;
        final String filter = "test/filter";
        final int QoS = QualityOfService.AT_LEAST_ONCE.getValue();
        final boolean noLocal = true;
        final int packetId = 24;
        final List<Integer> expectedReasonCodes = Arrays.asList(0);

        final List<Subscription> subscriptions = Arrays.asList(
            new Subscription(filter, QoS, noLocal, RETAIN_AS_PUBLISHED, RETAIN_HANDLING)
        );

        final CompletableFuture<Integer> subscribeFuture = new CompletableFuture<>();
        when(connection.subscribe(any(String.class), any(QualityOfService.class))).thenReturn(subscribeFuture);

        // move to connected state
        mqttConnectionImpl.connectionEvents.onConnectionResumed(true);

        subscribeFuture.complete(packetId);

        // WHEN
        SubAckInfo subAckInfo = mqttConnectionImpl.subscribe(timeoutSeconds, subscriptionId, null, subscriptions);

        // THEN
        // check result in subAckInfo
        assertNotNull(subAckInfo);
        assertEquals(expectedReasonCodes, subAckInfo.getReasonCodes());
        assertNull(subAckInfo.getReasonString());

        // check calls of `connection`
        ArgumentCaptor<String> argument1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<QualityOfService> argument2 = ArgumentCaptor.forClass(QualityOfService.class);
        verify(connection).subscribe(argument1.capture(), argument2.capture());

        final String actualFilter = argument1.getValue();
        assertEquals(filter, actualFilter);

        final QualityOfService actualQoS = argument2.getValue();
        assertEquals(QoS, actualQoS.getValue());
    }

    @Test
    void GIVEN_not_connected_state_WHEN_subscribe_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int subscriptionId = 22;
        final String filter = "test/filter";
        final int QoS = QualityOfService.AT_LEAST_ONCE.getValue();
        final boolean noLocal = true;

        final List<Subscription> subscriptions = Arrays.asList(
            new Subscription(filter, QoS, noLocal, RETAIN_AS_PUBLISHED, RETAIN_HANDLING)
        );

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.subscribe(timeoutSeconds, subscriptionId, null, subscriptions);
        }, "MQTT client is not in connected state");
    }

    @Test
    void GIVEN_timedout_WHEN_subscribe_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final Integer subscriptionId = null;
        final String filter = "test/filter";
        final int QoS = QualityOfService.AT_LEAST_ONCE.getValue();
        final boolean noLocal = true;

        final List<Subscription> subscriptions = Arrays.asList(
            new Subscription(filter, QoS, noLocal, RETAIN_AS_PUBLISHED, RETAIN_HANDLING)
        );

        final CompletableFuture<Integer> subscribeFuture = new CompletableFuture<>();
        when(connection.subscribe(any(String.class), any(QualityOfService.class))).thenReturn(subscribeFuture);

        // move to connected state
        mqttConnectionImpl.connectionEvents.onConnectionResumed(true);

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
                mqttConnectionImpl.subscribe(timeoutSeconds, subscriptionId, null, subscriptions);
        }, "Could not subscribe");

        verify(connection).subscribe(eq(filter), eq(QualityOfService.getEnumValueFromInteger(QoS)));
    }

    @Test
    void GIVEN_subscription_id_WHEN_subscribe_THEN_illegal_argument_exception() {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final int subscriptionId = 33;
        final String filter = "test/filter";
        final int QoS = QualityOfService.AT_LEAST_ONCE.getValue();
        final boolean noLocal = true;

        final List<Subscription> subscriptions = Arrays.asList(
            new Subscription(filter, QoS, noLocal, RETAIN_AS_PUBLISHED, RETAIN_HANDLING)
        );

        // move to connected state
        mqttConnectionImpl.connectionEvents.onConnectionResumed(true);

        // WHEN, THEN
        assertThrows(IllegalArgumentException.class, () -> {
                mqttConnectionImpl.subscribe(timeoutSeconds, subscriptionId, null, subscriptions);
        }, "MQTT v3.1.1 doesn't support subscription id");
    }

    @Test
    void GIVEN_multiple_subscriptions_WHEN_subscribe_THEN_illegal_argument_exception() {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final Integer subscriptionId = null;
        final String filter = "test/filter";
        final int QoS = QualityOfService.AT_LEAST_ONCE.getValue();
        final boolean noLocal = true;

        final List<Subscription> subscriptions = Arrays.asList(
            new Subscription(filter, QoS, noLocal, RETAIN_AS_PUBLISHED, RETAIN_HANDLING),
            new Subscription(filter, QoS, noLocal, RETAIN_AS_PUBLISHED, RETAIN_HANDLING)
        );

        // move to connected state
        mqttConnectionImpl.connectionEvents.onConnectionResumed(true);

        // WHEN, THEN
        assertThrows(IllegalArgumentException.class, () -> {
                mqttConnectionImpl.subscribe(timeoutSeconds, subscriptionId, null, subscriptions);
        }, "Iot device SDK MQTT v3.1.1 client does not support to subscribe on multiple filters at once");
    }


    @Test
    void GIVEN_unsubscribe_successful_WHEN_unsubscribe_THEN_positive_result() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final String filter = "test/filter";
        final int packetId = 24;
        final List<Integer> expectedReasonCodes = Arrays.asList(0);

        final List<String> filters = Arrays.asList(filter);

        final CompletableFuture<Integer> unsubscribeFuture = new CompletableFuture<>();
        when(connection.unsubscribe(any(String.class))).thenReturn(unsubscribeFuture);
        unsubscribeFuture.complete(packetId);

        // move to connected state
        mqttConnectionImpl.connectionEvents.onConnectionResumed(true);

        // WHEN
        UnsubAckInfo unsubAckInfo = mqttConnectionImpl.unsubscribe(timeoutSeconds, null, filters);

        // THEN
        // check result in subAckInfo
        assertNotNull(unsubAckInfo);
        assertEquals(expectedReasonCodes, unsubAckInfo.getReasonCodes());
        assertNull(unsubAckInfo.getReasonString());

        // check calls of `connection`
        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(connection).unsubscribe(argument.capture());

        final String actualFilter = argument.getValue();
        assertEquals(filter, actualFilter);
    }

    @Test
    void GIVEN_not_connected_state_WHEN_unsubscribe_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final String filter = "test/filter";
        final List<String> filters = Arrays.asList(filter);

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.unsubscribe(timeoutSeconds, null, filters);
        }, "MQTT client is not in connected state");
    }

    @Test
    void GIVEN_timedout_WHEN_unsubscribe_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final String filter = "test/filter";
        final List<String> filters = Arrays.asList(filter);

        final CompletableFuture<Integer> unsubscribeFuture = new CompletableFuture<>();
        when(connection.unsubscribe(any(String.class))).thenReturn(unsubscribeFuture);

        // move to connected state
        mqttConnectionImpl.connectionEvents.onConnectionResumed(true);

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
                mqttConnectionImpl.unsubscribe(timeoutSeconds, null, filters);
        }, "Could not unsubscribe");

        verify(connection).unsubscribe(eq(filter));
    }

    @Test
    void GIVEN_multiple_filters_WHEN_unsubscribe_THEN_illegal_argument_exception() {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final String filter = "test/filter";
        final List<String> filters = Arrays.asList(filter, filter);

        // move to connected state
        mqttConnectionImpl.connectionEvents.onConnectionResumed(true);

        // WHEN, THEN
        assertThrows(IllegalArgumentException.class, () -> {
                mqttConnectionImpl.unsubscribe(timeoutSeconds, null, filters);
        }, "Iot device SDK MQTT v3.1.1 client does not support to unsubscribe from multiple filters at once");
    }
}
