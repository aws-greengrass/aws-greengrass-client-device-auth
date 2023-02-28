/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt5.client.GRPCLink;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of gRPC bidirectional link.
 */
public class GRPCLinkImpl implements GRPCLink  {
    private static final int AUTOSELECT_PORT = 0;

    private static final Logger logger = Logger.getLogger(GRPCLinkImpl.class.getName());
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
        logger.log(Level.INFO, "Making gPRC link with {0}:{1} as {2}",
                    new Object[]{host, String.valueOf(port), agentId});

        String otfAddress = GRPCControlServer.buildAddress(host, port);
        GRPCDiscoveryClient client = new GRPCDiscoveryClient(agentId, otfAddress);
        String localIP = client.registerAgent();
        logger.log(Level.INFO, "Local address is {0}", localIP);

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
        logger.log(Level.INFO, "Handle gRPC requests");
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
        logger.log(Level.INFO, "Shutdown gPRC link");
        client.unregisterAgent(reason);
        server.close();
        client.close();
    }
}
