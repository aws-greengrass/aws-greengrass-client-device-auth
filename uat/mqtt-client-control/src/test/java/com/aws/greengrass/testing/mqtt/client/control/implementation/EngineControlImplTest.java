/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl;
import com.aws.greengrass.testing.mqtt.client.control.api.EngineControl;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EngineControlImplTest {

    private EngineControlImpl engineControl;

    @Mock
    private EngineControl.EngineEvents engineEvents;

    @Mock
    EngineControlImpl.AgentControlFactory agentControlFactory;


    @BeforeEach
    void setup() {
        engineControl = new EngineControlImpl(agentControlFactory);
    }

    @AfterEach
    void teardown() throws InterruptedException {
        engineControl.stopEngine();
        engineControl.awaitTermination();
    }


    @Test
    void GIVEN_engine_not_started_WHEN_start_engine_THEN_no_exceptions() throws IOException {
        // GIVEN
        final int port = 9131;

        // WHEN
        engineControl.startEngine(port, engineEvents);
    }

    @Test
    void GIVEN_engine_not_started_WHEN_get_bound_port_THEN_return_null() {
        // WHEN
        final Integer boundPort = engineControl.getBoundPort();

        // THEN
        assertNull(boundPort);
    }

    @Test
    void GIVEN_engine_started_on_fixed_port_WHEN_get_bound_port_THEN_return_same_value() throws IOException {
        // GIVEN
        final int port = 9132;
        engineControl.startEngine(port, engineEvents);

        // WHEN
        final Integer boundPort = engineControl.getBoundPort();

        // THEN
        assertNotNull(boundPort);
        assertEquals(port, boundPort);
    }

    @Test
    void GIVEN_engine_started_on_autoselected_port_WHEN_get_bound_port_THEN_get_some_value() throws IOException {
        // GIVEN
        final int port = 0;
        engineControl.startEngine(port, engineEvents);

        // WHEN
        final Integer boundPort = engineControl.getBoundPort();

        // THEN
        assertNotNull(boundPort);
        assertTrue(boundPort >= 1024);
    }

    @Test
    void GIVEN_engine_not_started_WHEN_is_engine_running_THEN_return_false() {
        // WHEN
        final boolean isRunning = engineControl.isEngineRunning();

        // THEN
        assertFalse(isRunning);
    }

    @Test
    void GIVEN_engine_started_WHEN_is_engine_running_THEN_return_true() throws IOException {
        // GIVEN
        final int port = 0;
        engineControl.startEngine(port, engineEvents);

        // WHEN
        final boolean isRunning = engineControl.isEngineRunning();

        // THEN
        assertTrue(isRunning);
    }

    @Test
    void GIVEN_no_agents_WHEN_get_agent_THEN_return_null() {
        // GIVEN
        final String agentId = "not_exist_agent";

        // WHEN
        final AgentControl agentControl = engineControl.getAgent(agentId);

        // THEN
        assertNull(agentControl);
    }

    @Test
    void GIVEN_registered_agent_WHEN_get_agent_THEN_return_valid_agent() {
        // GIVEN
        final String agentId = "agent000";
        final String agentAddress = "agent_address";
        final int agentPort = 43000;
        final AgentControlImpl agentControl = mock(AgentControlImpl.class);
        when(agentControlFactory.newAgentControl(any(EngineControlImpl.class), anyString(), anyString(), anyInt())).thenReturn(agentControl);
        engineControl.onDiscoveryAgent(agentId, agentAddress, agentPort);

        // WHEN
        final AgentControl actualAgentControl = engineControl.getAgent(agentId);

        // THEN
        assertSame(agentControl, actualAgentControl);
    }


    @Test
    void GIVEN_no_agents_WHEN_get_connection_control_THEN_return_null() {
        // GIVEN
        final String connectionName = "not_exist_connection";

        // WHEN
        final ConnectionControl connectionControl = engineControl.getConnectionControl(connectionName);

        // THEN
        assertNull(connectionControl);
    }

    @Test
    void GIVEN_registered_agent_without_connection_WHEN_get_connection_control_THEN_return_null() {
        // GIVEN
        final String connectionName = "connection_name";
        final String agentId = "agent001";
        final String agentAddress = "agent_address";
        final int agentPort = 43001;

        final AgentControlImpl agentControl = mock(AgentControlImpl.class);

        when(agentControlFactory.newAgentControl(any(EngineControlImpl.class), anyString(), anyString(), anyInt())).thenReturn(agentControl);
        engineControl.onDiscoveryAgent(agentId, agentAddress, agentPort);

        // WHEN
        final ConnectionControl actualConnectionControl = engineControl.getConnectionControl(connectionName);

        // THEN
        assertNull(actualConnectionControl);
    }

    @Test
    void GIVEN_registered_agent_with_connection_WHEN_get_connection_control_THEN_valid_connection_control() throws IOException {
        // GIVEN
        final String connectionName = "connection_name";
        final String agentId = "agent002";
        final String agentAddress = "agent_address";
        final int agentPort = 43002;

        final ConnectionControl connectionControl = mock(ConnectionControl.class);
        final AgentControlImpl agentControl = mock(AgentControlImpl.class);

        when(agentControl.getConnectionControl(eq(connectionName))).thenReturn(connectionControl);

        when(agentControlFactory.newAgentControl(any(EngineControlImpl.class), anyString(), anyString(), anyInt())).thenReturn(agentControl);
        engineControl.onDiscoveryAgent(agentId, agentAddress, agentPort);

        // WHEN
        final ConnectionControl actualConnectionControl = engineControl.getConnectionControl(connectionName);

        // THEN
        assertSame(connectionControl, actualConnectionControl);
    }


    @Test
    void GIVEN_engine_started_WHEN_stop_engine_THEN_engine_is_stopped() throws IOException, InterruptedException {
        // GIVEN
        final int port = 0;
        engineControl.startEngine(port, engineEvents);

        // WHEN
        engineControl.stopEngine();

        // THEN
        assertFalse(engineControl.isEngineRunning());
    }

    @Test
    void GIVEN_engine_started_WHEN_on_discovery_agent_THEN_agent_created_and_started_and_attached() throws IOException {
        // GIVEN
        engineControl.startEngine(0, engineEvents);

        final String agentId = "agent003";
        final String agentAddress = "agent_address";
        final int agentPort = 43003;

        final AgentControlImpl agentControl = mock(AgentControlImpl.class);

        when(agentControlFactory.newAgentControl(any(EngineControlImpl.class), anyString(), anyString(), anyInt())).thenReturn(agentControl);

        // WHEN
        engineControl.onDiscoveryAgent(agentId, agentAddress, agentPort);

        // THEN
        assertSame(agentControl, engineControl.getAgent(agentId));
        verify(agentControlFactory, times(1)).newAgentControl(eq(engineControl), eq(agentId), eq(agentAddress), eq(agentPort));
        verify(agentControl, times(1)).startAgent();
        verify(engineEvents).onAgentAttached(eq(agentControl));
    }


    @Test
    void GIVEN_engine_started_and_has_agent_WHEN_on_unregister_agent_THEN_agent_stopped_and_detached() throws IOException {
        // GIVEN
        engineControl.startEngine(0, engineEvents);

        final String agentId = "agent004";
        final String agentAddress = "agent_address";
        final int agentPort = 43004;

        final AgentControlImpl agentControl = mock(AgentControlImpl.class);

        when(agentControlFactory.newAgentControl(any(EngineControlImpl.class), anyString(), anyString(), anyInt())).thenReturn(agentControl);
        engineControl.onDiscoveryAgent(agentId, agentAddress, agentPort);

        // WHEN
        engineControl.onUnregisterAgent(agentId);

        // THEN
        assertNull(engineControl.getAgent(agentId));
        verify(agentControl, times(1)).stopAgent(false);
        verify(engineEvents).onAgentDeattached(eq(agentControl));
    }

    @Test
    void GIVEN_engine_started_and_has_agent_WHEN_on_message_recevied_THEN_callback_is_called() throws IOException {
        // GIVEN
        engineControl.startEngine(0, engineEvents);


        final String agentId = "agent005";
        final String agentAddress = "agent_address";
        final int agentPort = 43005;

        final AgentControlImpl agentControl = mock(AgentControlImpl.class);

        when(agentControlFactory.newAgentControl(any(EngineControlImpl.class), anyString(), anyString(), anyInt())).thenReturn(agentControl);
        engineControl.onDiscoveryAgent(agentId, agentAddress, agentPort);

        final int connectionId = 22;
        Mqtt5Message message = Mqtt5Message.newBuilder().build();

        // WHEN
        engineControl.onMessageReceived(agentId, connectionId, message);

        // THEN
        verify(agentControl).onMessageReceived(eq(connectionId), eq(message));
    }

    @Test
    void GIVEN_engine_started_and_has_agent_WHEN_on_mqtt_disconnect_THEN_callback_is_called() throws IOException {
        // GIVEN
        engineControl.startEngine(0, engineEvents);


        final String agentId = "agent006";
        final String agentAddress = "agent_address";
        final int agentPort = 43006;

        final AgentControlImpl agentControl = mock(AgentControlImpl.class);

        when(agentControlFactory.newAgentControl(any(EngineControlImpl.class), anyString(), anyString(), anyInt())).thenReturn(agentControl);
        engineControl.onDiscoveryAgent(agentId, agentAddress, agentPort);

        final int connectionId = 22;
        Mqtt5Disconnect disconnect = Mqtt5Disconnect.newBuilder().build();
        final String error = "test_error";

        // WHEN
        engineControl.onMqttDisconnect(agentId, connectionId, disconnect, error);

        // THEN
        verify(agentControl).onMqttDisconnect(eq(connectionId), eq(disconnect), eq(error));
    }

    @Test
    void GIVEN_control_WHEN_get_ips_THEN_XXX() throws IOException {
        // GIVEN

        // WHEN
        String[] ips = engineControl.getIPs();

        // THEN
        assertNotNull(ips);
        assertTrue(ips.length > 0);
    }
}
