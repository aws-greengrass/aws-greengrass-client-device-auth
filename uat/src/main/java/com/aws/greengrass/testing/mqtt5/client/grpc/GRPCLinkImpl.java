/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt5.client.GRPCLink;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Implementation of gRPC bidirectional link.
 */
public class GRPCLinkImpl implements GRPCLink {
    private static final Logger logger = LogManager.getLogger(GRPCLinkImpl.class);

    private static final int AUTOSELECT_PORT = 0;

    private final GRPCDiscoveryClient client;
    private final GRPCControlServer server;


    /**
     * Establish bidirectional link with the testing framework.
     *
     * @param agentId id of agent to identify control channel by gRPC server
     * @param host host name of gRPC server to connect to testing framework
     * @param port TCP port to connect to
     * @throws GRPCException on errors
     */
    public GRPCLinkImpl(String agentId, String host, int port) throws GRPCException {
        super();
        logger.atInfo().log("Making gPRC link with {}:{} as {}", host, port, agentId);

        String otfAddress = buildAddress(host, port);
        GRPCDiscoveryClient client = new GRPCDiscoveryClient(agentId, otfAddress);
        String localIP = client.registerAgent();
        logger.atInfo().log("Local address is {0}", localIP);

        try {
            GRPCControlServer server = new GRPCControlServer(client, localIP, AUTOSELECT_PORT);
            int servicePort = server.getPort();
            client.discoveryAgent(localIP, servicePort);
            this.client = client;
            this.server = server;
        } catch (IOException ex) {
            throw new GRPCException(ex);
        }
    }

    /**
     * Handle gRPC requests.
     *
     * @param mqttLib MQTT library handler
     * @return shutdown reason
     * @throws GRPCException on errors
     * @throws InterruptedException when thread has been interrupted
     */
    @Override
    public String handleRequests(MqttLib mqttLib) throws GRPCException, InterruptedException {
        logger.atInfo().log("Handle gRPC requests");
        server.waiting(mqttLib);
        return  "Agent shutdown by OTF request '" + server.getShutdownReason() + "'";
    }

    /**
     * Unregister MQTT client control in testing framework.
     *
     * @param reason reason of shutdown
     * @throws GRPCException on errors
     */
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
    private static String buildAddress(String host, int port) {
        return host + ":" + port;
    }
}
