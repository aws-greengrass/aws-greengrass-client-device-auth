/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GRPCLinkImplTest {
    private static final String AGENT_ID = "agent000";
    private static final String HOST = "test_host";
    private static final int PORT = 1974;
    private static final String LOCAL_IP = "local_ip";

    private static final int SERVICE_PORT = 11_393;

    private GRPCDiscoveryClient client;
    private GRPCControlServer server;

    private GRPCLinkImpl gRPCLinkImpl;

    @BeforeEach
    void setup() throws GRPCException, IOException {
        client = mock(GRPCDiscoveryClient.class);
        when(client.registerAgent()).thenReturn(LOCAL_IP);

        server = mock(GRPCControlServer.class);
        when(server.getPort()).thenReturn(SERVICE_PORT);

        GRPCLinkImpl.HalvesFactory halvesFactory = mock(GRPCLinkImpl.HalvesFactory.class);
        final String buildAddress = GRPCLinkImpl.buildAddress(HOST, PORT);
        when(halvesFactory.newClient(eq(AGENT_ID), eq(buildAddress))).thenReturn(client);
        when(halvesFactory.newServer(eq(client), eq(LOCAL_IP), eq(0))).thenReturn(server);

        gRPCLinkImpl = new GRPCLinkImpl(AGENT_ID, HOST, PORT, halvesFactory);

        verify(halvesFactory).newClient(eq(AGENT_ID), eq(buildAddress));
        verify(client).registerAgent();
        verify(halvesFactory).newServer(eq(client), eq(LOCAL_IP), eq(0));
        verify(server).getPort();
        verify(client).discoveryAgent(eq(LOCAL_IP), eq(SERVICE_PORT));
    }

    @SuppressWarnings("PMD.CloseResource")
    @Test
    void GIVEN_link_WHEN_handle_requests_THEN_server_is_called_correct_reason_returned() throws GRPCException, InterruptedException {
        // GIVEN
        final String reason = "test_reason";
        final String expectedShutdownReason = "Agent shutdown by OTF request '" + reason + "'";

        when(server.getShutdownReason()).thenReturn(reason);

        final MqttLib mqttLib = mock(MqttLib.class);

        // WHEN
        String shutdownReason = gRPCLinkImpl.handleRequests(mqttLib);

        // THEN
        assertEquals(expectedShutdownReason, shutdownReason);
        verify(server).waiting(eq(mqttLib));
    }

    @Test
    void GIVEN_link_WHEN_shutdown_THEN_client_and_server_were_closed() throws GRPCException {
        // GIVEN
        final String reason = "test_clients_reason";

        // WHEN
        gRPCLinkImpl.shutdown(reason);

        // THEN
        verify(client).unregisterAgent(eq(reason));
        verify(client).close();
        verify(server).close();
    }
}
