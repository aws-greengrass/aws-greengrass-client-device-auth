/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation;

import com.aws.greengrass.testing.mqtt.client.Mqtt5ConnAck;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Subscription;
import com.aws.greengrass.testing.mqtt.client.MqttCloseRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectReply;
import com.aws.greengrass.testing.mqtt.client.MqttConnectionId;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttPublishRequest;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.MqttUnsubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl.ConnectionEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionControlImplTest {
    private static final int CONNECTION_ID = 1974;
    private static final int DEFAULT_TIMEOUT = 1970;
    private static final int TIMEOUT = 2023;
    private static final String CONNECTION_NANE = "testConnection";
    private static final String AGENT_ID = "agent1";

    private ConnectionControlImpl connectionControl;

    private MqttConnectReply connectReply;
    private ConnectionEvents connectionEvents;
    private Mqtt5ConnAck connAck;
    private AgentControlImpl agent;

    @BeforeEach
    void setup() {
        MqttConnectionId connectionId = MqttConnectionId.newBuilder().setConnectionId(CONNECTION_ID).build();
        connAck = Mqtt5ConnAck.newBuilder().build();
        connectReply = MqttConnectReply.newBuilder()
                                        .setConnectionId(connectionId)
                                        .setConnAck(connAck)
                                        .build();

        connectionEvents = mock(ConnectionEvents.class);

        agent = mock(AgentControlImpl.class);
        when(agent.getAgentId()).thenReturn(AGENT_ID);
        when(agent.getTimeout()).thenReturn(DEFAULT_TIMEOUT);

        connectionControl = new ConnectionControlImpl(connectReply, connectionEvents, agent);
    }

    @Test
    void GIVEN_default_connection_control_WHEN_get_connection_id_THEN_expected() {
        assertEquals(CONNECTION_ID, connectionControl.getConnectionId());
    }

    @Test
    void GIVEN_default_connection_control_WHEN_get_connection_name_THEN_same_as_from_build_connection_name() {
        final String expected = ConnectionControlImpl.buildConnectionName(AGENT_ID, CONNECTION_ID);
        assertEquals(expected, connectionControl.getConnectionName());
    }

    @Test
    void GIVEN_connection_name_WHEN_set_and_get_connection_name_THEN_as_was_set() {
        connectionControl.setConnectionName(CONNECTION_NANE);
        assertEquals(CONNECTION_NANE, connectionControl.getConnectionName());
    }

    @Test
    void GIVEN_default_connection_control_WHEN_get_conn_ack_THEN_same_as_passed_to_contructor() {
        assertSame(connAck, connectionControl.getConnAck());
    }

    @Test
    void GIVEN_default_connection_control_WHEN_get_timeout_THEN_timeout_from_agent() {
        assertEquals(DEFAULT_TIMEOUT, connectionControl.getTimeout());
    }

    @Test
    void GIVEN_timeout_WHEN_set_and_get_timeout_THEN_as_was_set() {
        // WHEN
        connectionControl.setTimeout(TIMEOUT);

        // THEN
        assertEquals(TIMEOUT, connectionControl.getTimeout());
    }

    @Test
    void GIVEN_reason_WHEN_close_mqtt_connection_THEN_agents_method_is_called() {
        // GIVEN
        final int reason = 123;

        // WHEN
        connectionControl.closeMqttConnection(reason);

        // THEN
        verify(agent, times(1)).closeMqttConnection(any(MqttCloseRequest.class));
    }

    @Test
    void GIVEN_subscriptions_WHEN_subscribe_mqtt_THEN_agents_method_is_called() {
        // GIVEN
        final int subscriptionId = 344;
        Mqtt5Subscription subscription = Mqtt5Subscription.newBuilder().build();
        MqttSubscribeReply reply = MqttSubscribeReply.newBuilder().build();

        when(agent.subscribeMqtt(any(MqttSubscribeRequest.class))).thenReturn(reply);

        // WHEN
        final MqttSubscribeReply actual = connectionControl.subscribeMqtt(subscriptionId, subscription);

        // THEN
        assertSame(reply, actual);
        verify(agent, times(1)).subscribeMqtt(any(MqttSubscribeRequest.class));
    }

    @Test
    void GIVEN_filter_WHEN_unsubscribe_mqtt_THEN_agents_method_is_called() {
        // GIVEN
        String filter = "testFilter";
        MqttSubscribeReply reply = MqttSubscribeReply.newBuilder().build();

        when(agent.unsubscribeMqtt(any(MqttUnsubscribeRequest.class))).thenReturn(reply);

        // WHEN
        MqttSubscribeReply actual = connectionControl.unsubscribeMqtt(filter);

        // THEN
        assertSame(reply, actual);
        verify(agent, times(1)).unsubscribeMqtt(any(MqttUnsubscribeRequest.class));
    }

    @Test
    void GIVEN_message_WHEN_publish_mqtt_THEN_agents_method_is_called() {
        // GIVEN
        Mqtt5Message message = Mqtt5Message.newBuilder().build();
        MqttPublishReply reply = MqttPublishReply.newBuilder().build();

        when(agent.publishMqtt(any(MqttPublishRequest.class))).thenReturn(reply);

        // WHEN
        MqttPublishReply actual = connectionControl.publishMqtt(message);

        // THEN
        assertSame(reply, actual);
        verify(agent, times(1)).publishMqtt(any(MqttPublishRequest.class));
    }

    @Test
    void GIVEN_received_message_WHEN_on_message_received_THEN_callback_is_called() {
        // GIVEN
        Mqtt5Message message = Mqtt5Message.newBuilder().build();

        // WHEN
        connectionControl.onMessageReceived(message);

        // THEN
        verify(connectionEvents, times(1)).onMessageReceived(connectionControl, message);
    }

    @Test
    void GIVEN_disconnect_message_WHEN_on_mqtt_disconnect_THEN_callback_is_called() {
        // GIVEN
        final String error = "test_error_string";
        Mqtt5Disconnect mqttDisconnect = Mqtt5Disconnect.newBuilder().build();

        // WHEN
        connectionControl.onMqttDisconnect(mqttDisconnect, error);

        // THEN
        verify(connectionEvents, times(1)).onMqttDisconnect(eq(connectionControl), eq(mqttDisconnect), eq(error));
    }
}

