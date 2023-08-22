/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt5.client.DiscoveryClient;
import com.aws.greengrass.testing.mqtt5.client.GRPCLink;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Implementation of gRPC bidirectional link.
 */
public class GRPCLinkImpl implements GRPCLink {
    private static final Logger logger = LogManager.getLogger(GRPCLinkImpl.class);

    private static final int AUTOSELECT_PORT = 0;

    private final GRPCDiscoveryClient client;
    private final GRPCControlServer server;

    interface HalvesFactory {
        GRPCDiscoveryClient newClient(@NonNull String agentId, @NonNull String address) throws GRPCException;

        GRPCControlServer newServer(@NonNull GRPCDiscoveryClient client, @NonNull String host, int port)
                                throws IOException;
    }

    @AllArgsConstructor
    private class ClientConnectResult {
        GRPCDiscoveryClient client;
        String localIP;
    }

    /**
     * Creates and establishes bidirectional link with the control.
     *
     * @param agentId id of agent to identify control channel by gRPC server
     * @param hosts the list of host name or IP address of gRPC server to connect to
     * @param port TCP port to connect to
     * @throws GRPCException on errors
     */
    public GRPCLinkImpl(@NonNull String agentId, @NonNull List<String> hosts, int port) throws GRPCException {
        this(agentId, hosts, port, new HalvesFactory() {
            @Override
            public GRPCDiscoveryClient newClient(@NonNull String agentId, @NonNull String address)
                        throws GRPCException {
                return new GRPCDiscoveryClient(agentId, address);
            }

            @Override
            public GRPCControlServer newServer(@NonNull GRPCDiscoveryClient client, @NonNull String host, int port)
                        throws IOException {
                return new GRPCControlServer(client, host, port);
            }
        });
    }

    /**
     * Creates and establishes bidirectional link with the control.
     *
     * @param agentId the id of agent to identify control channel by gRPC server
     * @param hosts the list of host name or IP address of gRPC server to connect to
     * @param port the TCP port to connect to
     * @param halvesFactory the factory for client and server
     * @throws GRPCException on errors
     */
    GRPCLinkImpl(@NonNull String agentId, @NonNull List<String> hosts, int port, @NonNull HalvesFactory halvesFactory)
                    throws GRPCException {
        super();

        ClientConnectResult result = makeClientsConnection(agentId, hosts, port, halvesFactory);
        GRPCDiscoveryClient client = result.client;

        try {
            GRPCControlServer server = halvesFactory.newServer(client, result.localIP, AUTOSELECT_PORT);
            int servicePort = server.getPort();
            client.discoveryAgent(result.localIP, servicePort);
            this.client = client;
            this.server = server;
        } catch (IOException ex) {
            throw new GRPCException(ex);
        }
    }

    @Override
    public String handleRequests(@NonNull MqttLib mqttLib, @NonNull DiscoveryClient discoveryClient)
            throws GRPCException, InterruptedException {
        logger.atInfo().log("Handle gRPC requests");
        server.waiting(mqttLib, discoveryClient);
        return  "Agent shutdown by OTF request '" + server.getShutdownReason() + "'";
    }

    @Override
    public void shutdown(String reason) throws GRPCException {
        logger.atInfo().log("Shutdown gPRC link");
        client.unregisterAgent(reason);
        server.close();
        client.close();
    }

    /**
     * Build gRPC address string from host and port.
     *
     * @param host host part of gRPC address
     * @param port port part of gRPC address
     * @return address of gRPC service
     */
    static String buildAddress(String host, int port) {
        return host + ":" + port;
    }

    @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
    private ClientConnectResult makeClientsConnection(@NonNull String agentId, @NonNull List<String> hosts, int port,
                                                        @NonNull HalvesFactory halvesFactory) throws GRPCException {
        GRPCException lastException = null;
        try {
            for (String host : hosts) {
                logger.atInfo().log("Making gPRC client connection with {}:{} as {}...", host, port, agentId);

                String otfAddress = buildAddress(host, port);
                GRPCDiscoveryClient client = halvesFactory.newClient(agentId, otfAddress);
                String localIP = client.registerAgent();
                logger.atInfo().log("Client connection with Control is established, local address is {}", localIP);
                return new ClientConnectResult(client, localIP);
            }
        } catch (GRPCException ex) {
            lastException = ex;
        }
        throw lastException;
    }
}
