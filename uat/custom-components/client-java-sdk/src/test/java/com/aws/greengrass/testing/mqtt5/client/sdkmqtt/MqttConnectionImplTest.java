/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import software.amazon.awssdk.crt.mqtt5.Mqtt5Client;
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
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.mqtt5.OnConnectionFailureReturn;
import software.amazon.awssdk.crt.mqtt5.OnConnectionSuccessReturn;
import software.amazon.awssdk.crt.mqtt5.OnStoppedReturn;
import software.amazon.awssdk.crt.mqtt5.NegotiatedSettings;
import software.amazon.awssdk.crt.mqtt5.PublishResult;
import software.amazon.awssdk.crt.mqtt5.QOS;
import software.amazon.awssdk.crt.mqtt5.packets.ConnAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.DisconnectPacket;
import software.amazon.awssdk.crt.mqtt5.packets.PubAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.PublishPacket;
import software.amazon.awssdk.crt.mqtt5.packets.SubAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.SubscribePacket;
import software.amazon.awssdk.crt.mqtt5.packets.UnsubAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.UnsubscribePacket;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttConnectionImplTest {
    private static final int DEFAULT_TIMEOUT_SEC = 10;
    private static final int SHORT_TIMEOUT_SEC = 1;

    private GRPCClient grpcClient;
    private Mqtt5Client client;

    private MqttConnectionImpl mqttConnectionImpl;

    @BeforeEach
    void setup() {
        grpcClient = mock(GRPCClient.class);
        client = mock(Mqtt5Client.class);
        mqttConnectionImpl = new MqttConnectionImpl(grpcClient, client);
    }


    @Test
    void GIVEN_connnect_successful_WHEN_start_THEN_positive_result_with_conn_ack() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int connectionId = 1974;

        final ConnAckPacket.ConnectReasonCode reasonCode = ConnAckPacket.ConnectReasonCode.SUCCESS;
        final QOS maximumQOS = QOS.AT_LEAST_ONCE;
        final boolean sessionPresent = false;
        final int sessionExpiryInterval = 40_234;
        final int receiveMaximum = 713_234;
        final boolean retainAvailable = true;
        final int maximumPacketSize = 432_234;
        final String assignedClientIdentifier = "client_id_1";
        final String reasonString = "reason_string";
        final boolean wildcardSubscriptionsAvailable = true;
        final boolean subscriptionIdentifiersAvailable = false;
        final boolean sharedSubscriptionsAvailable = false;
        final int serverKeepAlive = 1239;
        final String responseInformation = "response_information";
        final String serverReference = "server_reference";

        final ConnAckPacket connAckPacket = mock(ConnAckPacket.class);
        when(connAckPacket.getReasonCode()).thenReturn(reasonCode);
        when(connAckPacket.getMaximumQOS()).thenReturn(maximumQOS);
        when(connAckPacket.getSessionPresent()).thenReturn(sessionPresent);
        when(connAckPacket.getSessionExpiryInterval()).thenReturn(Long.valueOf(sessionExpiryInterval));
        when(connAckPacket.getReceiveMaximum()).thenReturn(receiveMaximum);
        when(connAckPacket.getRetainAvailable()).thenReturn(retainAvailable);
        when(connAckPacket.getMaximumPacketSize()).thenReturn(Long.valueOf(maximumPacketSize));
        when(connAckPacket.getAssignedClientIdentifier()).thenReturn(assignedClientIdentifier);
        when(connAckPacket.getReasonString()).thenReturn(reasonString);
        when(connAckPacket.getWildcardSubscriptionsAvailable()).thenReturn(wildcardSubscriptionsAvailable);
        when(connAckPacket.getSubscriptionIdentifiersAvailable()).thenReturn(subscriptionIdentifiersAvailable);
        when(connAckPacket.getSharedSubscriptionsAvailable()).thenReturn(sharedSubscriptionsAvailable);
        when(connAckPacket.getServerKeepAlive()).thenReturn(serverKeepAlive);
        when(connAckPacket.getResponseInformation()).thenReturn(responseInformation);
        when(connAckPacket.getServerReference()).thenReturn(serverReference);

        final NegotiatedSettings negotiatedSettings = mock(NegotiatedSettings.class);
        when(negotiatedSettings.getAssignedClientID()).thenReturn(assignedClientIdentifier);

        final OnConnectionSuccessReturn onConnectionSuccessReturn = mock(OnConnectionSuccessReturn.class);
        when(onConnectionSuccessReturn.getConnAckPacket()).thenReturn(connAckPacket);
        when(onConnectionSuccessReturn.getNegotiatedSettings()).thenReturn(negotiatedSettings);

        mqttConnectionImpl.lifecycleEvents.onConnectionSuccess(client, onConnectionSuccessReturn);

        // WHEN
        ConnectResult connectResult = mqttConnectionImpl.start(timeoutSeconds, connectionId);

        // THEN
        verify(client).start();
        assertNotNull(connectResult);
        assertTrue(connectResult.isConnected());

        ConnAckInfo connAckInfo = connectResult.getConnAckInfo();
        assertNotNull(connAckInfo);

        assertEquals(sessionPresent, connAckInfo.getSessionPresent());
        assertEquals(reasonCode.getValue(), connAckInfo.getReasonCode());
        assertEquals(sessionExpiryInterval, connAckInfo.getSessionExpiryInterval());
        assertEquals(receiveMaximum, connAckInfo.getReceiveMaximum());
        assertEquals(maximumQOS.getValue(), connAckInfo.getMaximumQoS());
        assertEquals(retainAvailable, connAckInfo.getRetainAvailable());
        assertEquals(maximumPacketSize, connAckInfo.getMaximumPacketSize());
        assertEquals(assignedClientIdentifier, connAckInfo.getAssignedClientId());
        assertEquals(reasonString, connAckInfo.getReasonString());
        assertEquals(wildcardSubscriptionsAvailable, connAckInfo.getWildcardSubscriptionsAvailable());
        assertEquals(subscriptionIdentifiersAvailable, connAckInfo.getSubscriptionIdentifiersAvailable());
        assertEquals(sharedSubscriptionsAvailable, connAckInfo.getSharedSubscriptionsAvailable());
        assertEquals(serverKeepAlive, connAckInfo.getServerKeepAlive());
        assertEquals(responseInformation, connAckInfo.getResponseInformation());
        assertEquals(serverReference, connAckInfo.getServerReference());

        assertNull(connectResult.getError());
    }

    @Test
    void GIVEN_connect_banned_WHEN_start_THEN_negative_result_with_conn_ack() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int connectionId = 1975;

        final int crtErrorCode = 22;
        final String crtErrorString = CRT.awsErrorString(crtErrorCode);

        final ConnAckPacket.ConnectReasonCode reasonCode = ConnAckPacket.ConnectReasonCode.BANNED;
        final QOS maximumQOS = QOS.AT_LEAST_ONCE;
        final boolean sessionPresent = false;
        final int sessionExpiryInterval = 40_234;
        final int receiveMaximum = 713_234;
        final boolean retainAvailable = true;
        final int maximumPacketSize = 432_234;
        final String assignedClientIdentifier = "client_id_1";
        final String reasonString = "reason_string";
        final boolean wildcardSubscriptionsAvailable = true;
        final boolean subscriptionIdentifiersAvailable = false;
        final boolean sharedSubscriptionsAvailable = false;
        final int serverKeepAlive = 1239;
        final String responseInformation = "response_information";
        final String serverReference = "server_reference";

        final ConnAckPacket connAckPacket = mock(ConnAckPacket.class);
        when(connAckPacket.getReasonCode()).thenReturn(reasonCode);
        when(connAckPacket.getMaximumQOS()).thenReturn(maximumQOS);
        when(connAckPacket.getSessionPresent()).thenReturn(sessionPresent);
        when(connAckPacket.getSessionExpiryInterval()).thenReturn(Long.valueOf(sessionExpiryInterval));
        when(connAckPacket.getReceiveMaximum()).thenReturn(receiveMaximum);
        when(connAckPacket.getRetainAvailable()).thenReturn(retainAvailable);
        when(connAckPacket.getMaximumPacketSize()).thenReturn(Long.valueOf(maximumPacketSize));
        when(connAckPacket.getAssignedClientIdentifier()).thenReturn(assignedClientIdentifier);
        when(connAckPacket.getReasonString()).thenReturn(reasonString);
        when(connAckPacket.getWildcardSubscriptionsAvailable()).thenReturn(wildcardSubscriptionsAvailable);
        when(connAckPacket.getSubscriptionIdentifiersAvailable()).thenReturn(subscriptionIdentifiersAvailable);
        when(connAckPacket.getSharedSubscriptionsAvailable()).thenReturn(sharedSubscriptionsAvailable);
        when(connAckPacket.getServerKeepAlive()).thenReturn(serverKeepAlive);
        when(connAckPacket.getResponseInformation()).thenReturn(responseInformation);
        when(connAckPacket.getServerReference()).thenReturn(serverReference);

        final OnConnectionFailureReturn onConnectionFailureReturn = mock(OnConnectionFailureReturn.class);
        when(onConnectionFailureReturn.getConnAckPacket()).thenReturn(connAckPacket);
        when(onConnectionFailureReturn.getErrorCode()).thenReturn(crtErrorCode);

        mqttConnectionImpl.lifecycleEvents.onConnectionFailure(client, onConnectionFailureReturn);

        // WHEN
        ConnectResult connectResult = mqttConnectionImpl.start(timeoutSeconds, connectionId);

        // THEN
        verify(client).start();
        assertNotNull(connectResult);
        assertFalse(connectResult.isConnected());

        ConnAckInfo connAckInfo = connectResult.getConnAckInfo();
        assertNotNull(connAckInfo);

        assertEquals(sessionPresent, connAckInfo.getSessionPresent());
        assertEquals(reasonCode.getValue(), connAckInfo.getReasonCode());
        assertEquals(sessionExpiryInterval, connAckInfo.getSessionExpiryInterval());
        assertEquals(receiveMaximum, connAckInfo.getReceiveMaximum());
        assertEquals(maximumQOS.getValue(), connAckInfo.getMaximumQoS());
        assertEquals(retainAvailable, connAckInfo.getRetainAvailable());
        assertEquals(maximumPacketSize, connAckInfo.getMaximumPacketSize());
        assertEquals(assignedClientIdentifier, connAckInfo.getAssignedClientId());
        assertEquals(reasonString, connAckInfo.getReasonString());
        assertEquals(wildcardSubscriptionsAvailable, connAckInfo.getWildcardSubscriptionsAvailable());
        assertEquals(subscriptionIdentifiersAvailable, connAckInfo.getSubscriptionIdentifiersAvailable());
        assertEquals(sharedSubscriptionsAvailable, connAckInfo.getSharedSubscriptionsAvailable());
        assertEquals(serverKeepAlive, connAckInfo.getServerKeepAlive());
        assertEquals(responseInformation, connAckInfo.getResponseInformation());
        assertEquals(serverReference, connAckInfo.getServerReference());

        assertEquals(crtErrorString, connectResult.getError());
    }

    @Test
    void GIVEN_timedout_WHEN_start_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final int connectionId = 1976;

        // WHEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.start(timeoutSeconds, connectionId);
        }, "Exception occurred during connect");

        // THEN
        verify(client).start();
    }


    @Test
    void GIVEN_disconnect_successful_WHEN_disconnect_THEN_client_methods_are_called() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int reasonCode = 4;

        OnStoppedReturn onStoppedReturn = mock(OnStoppedReturn.class);

        mqttConnectionImpl.lifecycleEvents.onStopped(client, onStoppedReturn);

        // WHEN
        mqttConnectionImpl.disconnect(timeoutSeconds, reasonCode);

        // THEN
        ArgumentCaptor<DisconnectPacket> argument = ArgumentCaptor.forClass(DisconnectPacket.class);
        verify(client).stop(argument.capture());
        assertNotNull(argument.getValue());
        assertNotNull(argument.getValue().getReasonCode());
        assertEquals(reasonCode, argument.getValue().getReasonCode().getValue());

        verify(client).close();
    }

    @Test
    void GIVEN_timedout_WHEN_disconnect_THEN_exception() throws MqttException {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final int reasonCode = 4;

        // WHEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.disconnect(timeoutSeconds, reasonCode);
        }, "Could not disconnect");

        // THEN
        ArgumentCaptor<DisconnectPacket> argument = ArgumentCaptor.forClass(DisconnectPacket.class);
        verify(client).stop(argument.capture());
        assertNotNull(argument.getValue());
        assertNotNull(argument.getValue().getReasonCode());
        assertEquals(reasonCode, argument.getValue().getReasonCode().getValue());

        verify(client).close();
    }

    @Test
    void GIVEN_publish_successful_WHEN_publish_THEN_positive_result_with_pub_ack() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int QoS = QOS.AT_LEAST_ONCE.getValue();
        final boolean retain = true;
        final String topic = "test/topic";
        final byte[] payload = "test_payload".getBytes(StandardCharsets.UTF_8);

        Message message = Message.builder()
                                    .qos(QoS)
                                    .retain(retain)
                                    .topic(topic)
                                    .payload(payload)
                                    .build();

        final CompletableFuture<PublishResult> publishFuture = new CompletableFuture<>();
        when(client.publish(any(PublishPacket.class))).thenReturn(publishFuture);

        final PublishResult publishResult = mock(PublishResult.class);
        when(publishResult.getType()).thenReturn(PublishResult.PublishResultType.PUBACK);

        final PubAckPacket pubAckPacket = mock(PubAckPacket.class);
        final int reasonCode = PubAckPacket.PubAckReasonCode.SUCCESS.getValue();
        final String reasonString = "reason_success";
        when(pubAckPacket.getReasonCode()).thenReturn(PubAckPacket.PubAckReasonCode.getEnumValueFromInteger(reasonCode));
        when(pubAckPacket.getReasonString()).thenReturn(reasonString);

        when(publishResult.getResultPubAck()).thenReturn(pubAckPacket);

        // move to connected state
        mqttConnectionImpl.lifecycleEvents.onConnectionSuccess(client, null);

        publishFuture.complete(publishResult);

        // WHEN
        PubAckInfo pubAckInfo = mqttConnectionImpl.publish(timeoutSeconds, message);

        // THEN
        // check result in pubAckInfo
        assertNotNull(pubAckInfo);
        assertEquals(reasonCode, pubAckInfo.getReasonCode());
        assertEquals(reasonString, pubAckInfo.getReasonString());

        // check calls of `client`
        ArgumentCaptor<PublishPacket> argument = ArgumentCaptor.forClass(PublishPacket.class);
        verify(client).publish(argument.capture());

        PublishPacket publishPacket = argument.getValue();
        assertNotNull(publishPacket);

        assertEquals(QoS, publishPacket.getQOS().getValue());
        assertEquals(retain, publishPacket.getRetain());
        assertEquals(topic, publishPacket.getTopic());
        assertArrayEquals(payload, publishPacket.getPayload());
    }

    @Test
    void GIVEN_not_connected_state_WHEN_publish_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int QoS = QOS.AT_LEAST_ONCE.getValue();
        final boolean retain = true;
        final String topic = "test/topic";
        final byte[] payload = "test_payload".getBytes(StandardCharsets.UTF_8);

        Message message = Message.builder()
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
        final int QoS = QOS.AT_LEAST_ONCE.getValue();
        final boolean retain = true;
        final String topic = "test/topic";
        final byte[] payload = "test_payload".getBytes(StandardCharsets.UTF_8);

        Message message = Message.builder()
                                    .qos(QoS)
                                    .retain(retain)
                                    .topic(topic)
                                    .payload(payload)
                                    .build();

        final CompletableFuture<PublishResult> publishFuture = new CompletableFuture<>();
        when(client.publish(any(PublishPacket.class))).thenReturn(publishFuture);

        // move to connected state
        mqttConnectionImpl.lifecycleEvents.onConnectionSuccess(client, null);


        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.publish(timeoutSeconds, message);
        }, "Could not publish message");
    }


    @Test
    void GIVEN_subscribe_successful_WHEN_subscribe_THEN_positive_result_with_sub_ack() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int subscriptionId = 22;
        final String filter0 = "test/filter1";
        final String filter1 = "test/filter2";
        final int QoS = QOS.AT_LEAST_ONCE.getValue();
        final boolean noLocal = true;
        final boolean retainAsPublished = false;
        final int retainHandling = SubscribePacket.RetainHandlingType.SEND_ON_SUBSCRIBE.getValue();

        final List<Subscription> subscriptions = Arrays.asList(
            new Subscription(filter0, QoS, noLocal, retainAsPublished, retainHandling),
            new Subscription(filter1, QoS, noLocal, retainAsPublished, retainHandling)
        );

        final CompletableFuture<SubAckPacket> subscribeFuture = new CompletableFuture<>();
        when(client.subscribe(any(SubscribePacket.class))).thenReturn(subscribeFuture);

        final SubAckPacket.SubAckReasonCode reasonCode = SubAckPacket.SubAckReasonCode.GRANTED_QOS_1;
        final SubAckPacket subAckPacket = mock(SubAckPacket.class);

        final List<SubAckPacket.SubAckReasonCode> reasonCodes = Arrays.asList(reasonCode, reasonCode);
        final List<Integer> expectedReasonCodes = reasonCodes.stream().map(c -> c == null ? null : c.getValue()).collect(Collectors.toList());
        final String reasonString = "reason_success";

        when(subAckPacket.getReasonCodes()).thenReturn(reasonCodes);
        when(subAckPacket.getReasonString()).thenReturn(reasonString);

        // move to connected state
        mqttConnectionImpl.lifecycleEvents.onConnectionSuccess(client, null);

        subscribeFuture.complete(subAckPacket);

        // WHEN
        SubAckInfo subAckInfo = mqttConnectionImpl.subscribe(timeoutSeconds, subscriptionId, subscriptions);

        // THEN
        // check result in subAckInfo
        assertNotNull(subAckInfo);
        assertEquals(expectedReasonCodes, subAckInfo.getReasonCodes());
        assertEquals(reasonString, subAckInfo.getReasonString());

        // check calls of `client`
        ArgumentCaptor<SubscribePacket> argument = ArgumentCaptor.forClass(SubscribePacket.class);
        verify(client).subscribe(argument.capture());

        SubscribePacket subscribePacket = argument.getValue();
        assertNotNull(subscribePacket);

        assertEquals(subscriptionId, subscribePacket.getSubscriptionIdentifier());
        final List<SubscribePacket.Subscription> actualSubscriptions = subscribePacket.getSubscriptions();
        assertNotNull(actualSubscriptions);
        assertEquals(2, actualSubscriptions.size());

        for (SubscribePacket.Subscription subscription : actualSubscriptions) {
            assertEquals(QoS, subscription.getQOS().getValue());
            assertEquals(noLocal, subscription.getNoLocal());
            assertEquals(retainAsPublished, subscription.getRetainAsPublished());
            assertEquals(retainHandling, subscription.getRetainHandlingType().getValue());
        }
        assertEquals(filter0, actualSubscriptions.get(0).getTopicFilter());
        assertEquals(filter1, actualSubscriptions.get(1).getTopicFilter());
    }

    @Test
    void GIVEN_not_connected_state_WHEN_subscribe_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final int subscriptionId = 22;
        final String filter0 = "test/filter1";
        final String filter1 = "test/filter2";
        final int QoS = QOS.AT_LEAST_ONCE.getValue();
        final boolean noLocal = true;
        final boolean retainAsPublished = false;
        final int retainHandling = SubscribePacket.RetainHandlingType.SEND_ON_SUBSCRIBE.getValue();

        final List<Subscription> subscriptions = Arrays.asList(
            new Subscription(filter0, QoS, noLocal, retainAsPublished, retainHandling),
            new Subscription(filter1, QoS, noLocal, retainAsPublished, retainHandling)
        );

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.subscribe(timeoutSeconds, subscriptionId, subscriptions);
        }, "MQTT client is not in connected state");
    }

    @Test
    void GIVEN_timedout_WHEN_subscribe_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final int subscriptionId = 22;
        final String filter0 = "test/filter1";
        final String filter1 = "test/filter2";
        final int QoS = QOS.AT_LEAST_ONCE.getValue();
        final boolean noLocal = true;
        final boolean retainAsPublished = false;
        final int retainHandling = SubscribePacket.RetainHandlingType.SEND_ON_SUBSCRIBE.getValue();

        final List<Subscription> subscriptions = Arrays.asList(
            new Subscription(filter0, QoS, noLocal, retainAsPublished, retainHandling),
            new Subscription(filter1, QoS, noLocal, retainAsPublished, retainHandling)
        );

        final CompletableFuture<SubAckPacket> subscribeFuture = new CompletableFuture<>();
        when(client.subscribe(any(SubscribePacket.class))).thenReturn(subscribeFuture);

        // move to connected state
        mqttConnectionImpl.lifecycleEvents.onConnectionSuccess(client, null);

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
                mqttConnectionImpl.subscribe(timeoutSeconds, subscriptionId, subscriptions);
        }, "Could not subscribe");

    }


    @Test
    void GIVEN_unsubscribe_successful_WHEN_unsubscribe_THEN_positive_result_with_unsub_ack() throws MqttException {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final UnsubAckPacket.UnsubAckReasonCode reasonCode = UnsubAckPacket.UnsubAckReasonCode.SUCCESS;
        final List<String> filters = Arrays.asList("test/filter1", "test/filter2");
        final String reasonString = "reason_success";

        final CompletableFuture<UnsubAckPacket> unsubscribeFuture = new CompletableFuture<>();
        when(client.unsubscribe(any(UnsubscribePacket.class))).thenReturn(unsubscribeFuture);

        final UnsubAckPacket unsubAckPacket = mock(UnsubAckPacket.class);
        final List<UnsubAckPacket.UnsubAckReasonCode> reasonCodes = Arrays.asList(reasonCode, reasonCode);
        final List<Integer> expectedReasonCodes = reasonCodes.stream().map(c -> c == null ? null : c.getValue()).collect(Collectors.toList());
        when(unsubAckPacket.getReasonCodes()).thenReturn(reasonCodes);
        when(unsubAckPacket.getReasonString()).thenReturn(reasonString);

        // move to connected state
        mqttConnectionImpl.lifecycleEvents.onConnectionSuccess(client, null);

        unsubscribeFuture.complete(unsubAckPacket);

        // WHEN
        UnsubAckInfo unsubAckInfo = mqttConnectionImpl.unsubscribe(timeoutSeconds, filters);

        // THEN
        // check result in unsubAckInfo
        assertNotNull(unsubAckInfo);
        assertEquals(expectedReasonCodes, unsubAckInfo.getReasonCodes());
        assertEquals(reasonString, unsubAckInfo.getReasonString());

        // check calls of `client`
        ArgumentCaptor<UnsubscribePacket> argument = ArgumentCaptor.forClass(UnsubscribePacket.class);
        verify(client).unsubscribe(argument.capture());

        UnsubscribePacket unsubscribePacket = argument.getValue();
        assertNotNull(unsubscribePacket);
        assertEquals(filters, unsubscribePacket.getSubscriptions());
    }

    @Test
    void GIVEN_not_connected_state_WHEN_unsubscribe_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = DEFAULT_TIMEOUT_SEC;
        final List<String> filters = Arrays.asList("test/filter1", "test/filter2");

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.unsubscribe(timeoutSeconds, filters);
        }, "MQTT client is not in connected state");
    }

    @Test
    void GIVEN_timedout_WHEN_unsubscribe_THEN_exception() {
        // GIVEN
        final long timeoutSeconds = SHORT_TIMEOUT_SEC;
        final List<String> filters = Arrays.asList("test/filter1", "test/filter2");

        final CompletableFuture<UnsubAckPacket> unsubscribeFuture = new CompletableFuture<>();
        when(client.unsubscribe(any(UnsubscribePacket.class))).thenReturn(unsubscribeFuture);

        // move to connected state
        mqttConnectionImpl.lifecycleEvents.onConnectionSuccess(client, null);

        // WHEN, THEN
        assertThrows(MqttException.class, () -> {
            mqttConnectionImpl.unsubscribe(timeoutSeconds, filters);
        }, "Could not unsubscribe");
    }
}
