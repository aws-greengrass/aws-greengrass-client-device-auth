/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt.client.MqttAgentDiscoveryGrpc;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of gRPC server handles requests of OTF.
 */
class GRPCControlServer extends MqttAgentDiscoveryGrpc.MqttAgentDiscoveryImplBase {
    private static final Logger logger = Logger.getLogger(GRPCControlServer.class.getName());

    @SuppressWarnings("PMD.UnusedPrivateField") // TODO: remove
    private final GRPCDiscoveryClient client;
    private final Server server;
    private final int boundPort;
    @SuppressWarnings("PMD.UnusedPrivateField") // TODO: remove
    private MqttLib mqttLib;


    /**
     * Build gRPC address string from host and port.
     *
     * @param host host part of gRPC address
     * @param port port part of gRPC address
     * @return address of gRPC service
     */
    public static String buildAddress(String host, int port) {
        return host + ":" + port;
    }

    /**
     * Create instance of gRPC server.
     *
     * @param client reference to gRPC client
     * @param host bind address
     * @param port bind port, or 0 to autoselect
     * @throws IOException on errors
     */
    public GRPCControlServer(GRPCDiscoveryClient client, String host, int port) throws IOException {
        super();
        this.client = client;

        // String address = buildAddress(host, port);
        server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                    .addService(this)
                    .build()
                    .start();

        this.boundPort = server.getPort();
        logger.log(Level.INFO, "GRPCControlServer created and listed on %s:%hu",
                    new Object[]{host, String.valueOf(boundPort)});
    }

    public int getPort() {
        return boundPort;
    }

    public String getShutdownReason() {
        return null;
    }


    public void waiting(MqttLib mqttLib) throws InterruptedException {
        this.mqttLib = mqttLib;
        server.awaitTermination();
    }

    public void close() {
        server.shutdown(); // or       shutdownNow()
    }

}
