/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.MqttCloseRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectReply;
import com.aws.greengrass.testing.mqtt.client.MqttConnectRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectionId;
import com.aws.greengrass.testing.mqtt.client.MqttClientControlGrpc;
import com.aws.greengrass.testing.mqtt.client.MqttPublishRequest;
import com.aws.greengrass.testing.mqtt.client.MqttUnsubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.ShutdownRequest;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentControlImplTest {

    private static final String DEFAULT_AGENT_ID = "agent1";
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    private static final String DEFAULT_ADDRESS = "127.0.0.1";
    private static final int DEFAULT_PORT = 8474;
    private static final int TEST_TIMEOUT = 44;

    private ManagedChannel channel;
    private MqttClientControlGrpc.MqttClientControlBlockingStub blockingStub;
    private AgentControlImpl.ConnectionControlFactory connectionControlFactory;
    private AgentControlImpl agentControl;

    @BeforeEach
    void setup() {
        connectionControlFactory = mock(AgentControlImpl.ConnectionControlFactory.class);
        channel = mock(ManagedChannel.class);
        blockingStub = mock(MqttClientControlGrpc.MqttClientControlBlockingStub.class);
        agentControl = new AgentControlImpl(DEFAULT_AGENT_ID, DEFAULT_ADDRESS, DEFAULT_PORT, connectionControlFactory,
                                            channel, blockingStub);
    }

    @AfterEach
    void teardown() {
        agentControl.stopAgent();
    }

    @Test
    void GIVEN_agent_control_WHEN_get_timeout_THEN_return_default_timeout() {
        // GIVEN
        final int expected = AgentControl.DEFAULT_TIMEOUT;

        // WHEN
        final int actualTimeout = agentControl.getTimeout();

        // THEN
        assertEquals(expected, actualTimeout);
    }

    @Test
    void GIVEN_agent_control_WHEN_set_and_get_timeout_THEN_get_return_value_was_set() {
        // GIVEN
        final int expected = TEST_TIMEOUT;

        // WHEN
        agentControl.setTimeout(expected);
        final int actualTimeout = agentControl.getTimeout();

        // THEN
        assertEquals(expected, actualTimeout);
    }

    // TODO
    // startAgent
    // stopAgent

    @Test
    void GIVEN_agent_control_WHEN_get_agent_id_THEN_return_valid_value() {
        // GIVEN
        final String expected = DEFAULT_AGENT_ID;

        // WHEN
        final String actual = agentControl.getAgentId();

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void GIVEN_agent_control_without_connections_WHEN_get_connection_control_THEN_return_null() {
        // GIVEN
        final String connectionName = "not_exist_connection";

        // WHEN
        final ConnectionControl actualConnectionControl = agentControl.getConnectionControl(connectionName);

        // THEN
        assertNull(actualConnectionControl);
    }


    @Test
    void GIVEN_agent_control_has_connection_WHEN_get_connection_control_THEN_return_connection_control() {
        // GIVEN
        final String connectionName = "connection000";
        final int connectionIdInt = 1974;
        MqttConnectionId connectionId = MqttConnectionId.newBuilder().setConnectionId(connectionIdInt).build();
        MqttConnectReply response = MqttConnectReply.newBuilder()
                                                .setConnected(true)
                                                .setConnectionId(connectionId)
                                                .build();
        when(blockingStub.createMqttConnection(any(MqttConnectRequest.class))).thenReturn(response);

        MqttConnectRequest connectRequest = MqttConnectRequest.newBuilder().build();
        AgentControl.ConnectionEvents connectionEvents = mock(AgentControl.ConnectionEvents.class);


        ConnectionControlImpl connectionControl = mock(ConnectionControlImpl.class);
        when(connectionControl.getConnectionId()).thenReturn(connectionIdInt);
        when(connectionControl.getConnectionName()).thenReturn(connectionName);

        when(connectionControlFactory.newConnectionControl(eq(response), eq(connectionEvents), eq(agentControl))).thenReturn(connectionControl);
        agentControl.createMqttConnection(connectRequest, connectionEvents);

        // WHEN
        final ConnectionControl actualConnectionControl = agentControl.getConnectionControl(connectionName);

        // THEN
        assertSame(connectionControl, actualConnectionControl);
    }

    @Test
    void GIVEN_agent_control_WHEN_shutdown_agent_THEN_stub_is_called_with_same_reason() {
        // GIVEN
        final String reason = "test_reason";

        // WHEN
        agentControl.shutdownAgent(reason);

        // THEN
        ArgumentCaptor<ShutdownRequest> argument = ArgumentCaptor.forClass(ShutdownRequest.class);
        verify(blockingStub).shutdownAgent(argument.capture());
        assertEquals(reason, argument.getValue().getReason());
    }

    @Test
    void GIVEN_agent_control_WHEN_create_mqtt_connection_THEN_connection_control_created() {
        // GIVEN
        final int connectionIdInt = 1975;
        MqttConnectionId connectionId = MqttConnectionId.newBuilder().setConnectionId(connectionIdInt).build();
        MqttConnectReply response = MqttConnectReply.newBuilder()
                                                .setConnected(true)
                                                .setConnectionId(connectionId)
                                                .build();
        MqttConnectRequest connectRequest = MqttConnectRequest.newBuilder().build();
        when(blockingStub.createMqttConnection(eq(connectRequest))).thenReturn(response);
        AgentControl.ConnectionEvents connectionEvents = mock(AgentControl.ConnectionEvents.class);

        ConnectionControlImpl connectionControl = mock(ConnectionControlImpl.class);
        when(connectionControl.getConnectionId()).thenReturn(connectionIdInt);
        when(connectionControlFactory.newConnectionControl(eq(response), eq(connectionEvents), eq(agentControl))).thenReturn(connectionControl);

        // WHEN
        ConnectionControl actualConnectionControl = agentControl.createMqttConnection(connectRequest, connectionEvents);

        // THEN
        assertSame(connectionControl, actualConnectionControl);
        verify(blockingStub).createMqttConnection(eq(connectRequest));
    }

    @Test
    void GIVEN_agent_control_WHEN_subscribe_mqtt_THEN_stub_is_called() {
        // GIVEN
        final int connectionIdInt = 1976;
        MqttConnectionId connectionId = MqttConnectionId.newBuilder().setConnectionId(connectionIdInt).build();
        MqttSubscribeRequest subscribeRequest = MqttSubscribeRequest.newBuilder().setConnectionId(connectionId).build();

        // WHEN
        agentControl.subscribeMqtt(subscribeRequest);

        // THEN
        verify(blockingStub).subscribeMqtt(eq(subscribeRequest));
    }

    @Test
    void GIVEN_agent_control_WHEN_unsubscribe_mqtt_THEN_stub_is_called() {
        // GIVEN
        final int connectionIdInt = 1977;
        MqttConnectionId connectionId = MqttConnectionId.newBuilder().setConnectionId(connectionIdInt).build();
        MqttUnsubscribeRequest unsubscribeRequest = MqttUnsubscribeRequest.newBuilder().setConnectionId(connectionId).build();

        // WHEN
        agentControl.unsubscribeMqtt(unsubscribeRequest);

        // THEN
        verify(blockingStub).unsubscribeMqtt(eq(unsubscribeRequest));
    }

    @Test
    void GIVEN_agent_control_WHEN_publish_mqtt_THEN_stub_is_called() {
        // GIVEN
        final int connectionIdInt = 1978;
        MqttConnectionId connectionId = MqttConnectionId.newBuilder().setConnectionId(connectionIdInt).build();
        Mqtt5Message message = Mqtt5Message.newBuilder().setTopic("/topic").build();
        MqttPublishRequest publishRequest = MqttPublishRequest.newBuilder().setConnectionId(connectionId).setMsg(message).build();

        // WHEN
        agentControl.publishMqtt(publishRequest);

        // THEN
        verify(blockingStub).publishMqtt(eq(publishRequest));
    }

    @Test
    void GIVEN_agent_control_WHEN_close_mqtt_connection_THEN_stub_is_called() {
        // GIVEN
        final int connectionIdInt = 1979;
        MqttConnectionId connectionId = MqttConnectionId.newBuilder().setConnectionId(connectionIdInt).build();
        MqttCloseRequest closeRequest = MqttCloseRequest.newBuilder().setConnectionId(connectionId).build();

        // WHEN
        agentControl.closeMqttConnection(closeRequest);

        // THEN
        verify(blockingStub).closeMqttConnection(eq(closeRequest));
    }

    @Test
    void GIVEN_agent_control_has_connection_WHEN_on_message_received_THEN_stub_is_called() {
        // GIVEN
        final int connectionIdInt = 1980;
        MqttConnectionId connectionId = MqttConnectionId.newBuilder().setConnectionId(connectionIdInt).build();
        MqttConnectReply response = MqttConnectReply.newBuilder()
                                                .setConnected(true)
                                                .setConnectionId(connectionId)
                                                .build();
        when(blockingStub.createMqttConnection(any(MqttConnectRequest.class))).thenReturn(response);

        MqttConnectRequest connectRequest = MqttConnectRequest.newBuilder().build();
        AgentControl.ConnectionEvents connectionEvents = mock(AgentControl.ConnectionEvents.class);

        ConnectionControlImpl connectionControl = mock(ConnectionControlImpl.class);
        when(connectionControl.getConnectionId()).thenReturn(connectionIdInt);
        when(connectionControlFactory.newConnectionControl(any(MqttConnectReply.class), any(AgentControl.ConnectionEvents.class), any(AgentControlImpl.class))).thenReturn(connectionControl);

        agentControl.createMqttConnection(connectRequest, connectionEvents);

        Mqtt5Message message = Mqtt5Message.newBuilder().setTopic("/topic").build();

        // WHEN
        agentControl.onMessageReceived(connectionIdInt, message);

        // THEN
        verify(connectionControl).onMessageReceived(eq(message));
    }

    @Test
    void GIVEN_agent_control_has_connection_WHEN_on_mqtt_disconnect_THEN_stub_is_called() {
        // GIVEN
        final String error = "error_string";
        final int connectionIdInt = 1981;
        MqttConnectionId connectionId = MqttConnectionId.newBuilder().setConnectionId(connectionIdInt).build();
        MqttConnectReply response = MqttConnectReply.newBuilder()
                                                .setConnected(true)
                                                .setConnectionId(connectionId)
                                                .build();
        when(blockingStub.createMqttConnection(any(MqttConnectRequest.class))).thenReturn(response);

        MqttConnectRequest connectRequest = MqttConnectRequest.newBuilder().build();
        AgentControl.ConnectionEvents connectionEvents = mock(AgentControl.ConnectionEvents.class);

        ConnectionControlImpl connectionControl = mock(ConnectionControlImpl.class);
        when(connectionControl.getConnectionId()).thenReturn(connectionIdInt);
        when(connectionControlFactory.newConnectionControl(any(MqttConnectReply.class), any(AgentControl.ConnectionEvents.class), any(AgentControlImpl.class))).thenReturn(connectionControl);

        agentControl.createMqttConnection(connectRequest, connectionEvents);

        Mqtt5Disconnect disconnect = Mqtt5Disconnect.newBuilder().build();

        // WHEN
        agentControl.onMqttDisconnect(connectionIdInt, disconnect, error);

        // THEN
        verify(connectionControl).onMqttDisconnect(eq(disconnect), eq(error));
    }

    // onMqttDisconnect
}