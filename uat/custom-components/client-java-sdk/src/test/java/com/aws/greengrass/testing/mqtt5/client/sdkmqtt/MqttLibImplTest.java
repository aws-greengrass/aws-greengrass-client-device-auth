/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.GRPCClient;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib.ConnectionParams;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import com.aws.greengrass.testing.mqtt5.client.sdkmqtt.MqttLibImpl.ConnectionFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class MqttLibImplTest {

    private ConnectionFactory connectionFactory;

    private MqttLibImpl mqttLibImpl;

    @BeforeEach
    void setup() {
        connectionFactory = mock(ConnectionFactory.class);
        mqttLibImpl = new MqttLibImpl(connectionFactory);
    }

    @Test
    void GIVEN_factory_provides_connection_WHEN_create_connection_THEN_factory_is_called() throws MqttException {
        // GIVEN
        final MqttConnectionImpl expectedMqttConnection = mock(MqttConnectionImpl.class);
        when(connectionFactory.newConnection(any(ConnectionParams.class), any(GRPCClient.class))).thenReturn(expectedMqttConnection);

        final ConnectionParams connectionParams = mock(ConnectionParams.class);
        final GRPCClient gRPCClient = mock(GRPCClient.class);

        // WHEN
        final MqttConnection connection = mqttLibImpl.createConnection(connectionParams, gRPCClient);

        // THEN
        assertSame(expectedMqttConnection, connection);
        verify(connectionFactory).newConnection(eq(connectionParams), eq(gRPCClient));
    }

    @Test
    void GIVEN_connection_WHEN_register_connection_THEN_registered_with_id_1() throws MqttException {
        // GIVEN
        final MqttConnection mqttConnection = mock(MqttConnection.class);

        // WHEN
        int connectionId = mqttLibImpl.registerConnection(mqttConnection);

        // THEN
        assertEquals(1, connectionId);
    }

    @Test
    void GIVEN_register_twice_WHEN_register_connection_THEN_registered_with_next_id() throws MqttException {
        // GIVEN
        final MqttConnection mqttConnection = mock(MqttConnection.class);

        // WHEN
        final int connectionId1 = mqttLibImpl.registerConnection(mqttConnection);
        final int connectionId2 = mqttLibImpl.registerConnection(mqttConnection);

        // THEN
        assertEquals(1, connectionId1);
        assertEquals(2, connectionId2);
    }

    @Test
    void GIVEN_connection_id_does_not_exist_WHEN_unregister_connection_THEN_return_null() throws MqttException {
        // GIVEN
        final int connectionId = 22;

        // WHEN
        MqttConnection mqttConnection = mqttLibImpl.unregisterConnection(connectionId);

        // THEN
        assertNull(mqttConnection);
    }

    @Test
    void GIVEN_connection_id_exist_WHEN_unregister_connection_THEN_return_connection() throws MqttException {
        // GIVEN
        final MqttConnection mqttConnection = mock(MqttConnection.class);
        final int connectionId = mqttLibImpl.registerConnection(mqttConnection);

        // WHEN
        MqttConnection actualMqttConnection = mqttLibImpl.unregisterConnection(connectionId);

        // THEN
        assertSame(mqttConnection, actualMqttConnection);
    }

    @Test
    void GIVEN_unregister_twice_WHEN_unregister_connection_THEN_second_return_null() throws MqttException {
        // GIVEN
        final MqttConnection mqttConnection = mock(MqttConnection.class);
        final int connectionId = mqttLibImpl.registerConnection(mqttConnection);

        // WHEN
        final MqttConnection actualMqttConnection = mqttLibImpl.unregisterConnection(connectionId);
        final MqttConnection actualSecondMqttConnection = mqttLibImpl.unregisterConnection(connectionId);

        // THEN
        assertSame(mqttConnection, actualMqttConnection);
        assertNull(actualSecondMqttConnection);
    }

    @Test
    void GIVEN_connection_id_does_not_exist_WHEN_get_connection_THEN_return_null() throws MqttException {
        // GIVEN
        final int connectionId = 22;

        // WHEN
        MqttConnection mqttConnection = mqttLibImpl.getConnection(connectionId);

        // THEN
        assertNull(mqttConnection);
    }

    @Test
    void GIVEN_connection_id_exist_WHEN_get_connection_THEN_return_connection() throws MqttException {
        // GIVEN
        final MqttConnection mqttConnection = mock(MqttConnection.class);
        final int connectionId = mqttLibImpl.registerConnection(mqttConnection);

        // WHEN
        MqttConnection actualMqttConnection = mqttLibImpl.getConnection(connectionId);

        // THEN
        assertSame(mqttConnection, actualMqttConnection);
    }

}
