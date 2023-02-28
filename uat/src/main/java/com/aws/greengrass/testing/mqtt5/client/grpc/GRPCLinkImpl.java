/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt5.client.GRPCLink;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;

/**
 * Implementation of gRPC bidirectional link.
 */
public class GRPCLinkImpl implements GRPCLink  {

    /**
     * Establish bidirectional link with the testing framework.
     *
     * @param agentId id of agent to identify control channel by gRPC server
     * @param host host name of gRPC server to connect to testing framework
     * @param port TCP port to connect to
     * @throws GRPCException on errors
     */
    public GRPCLinkImpl(String agentId, String host, int port) throws GRPCException {
    }

    /**
     * Handle gRPC requests.
     *
     * @param mqttLib MQTT library handler
     * @return shutdown reason
     * @throws GRPCException on errors
     */
    public String handleRequests(MqttLib mqttLib) throws GRPCException {
        return null;
    }

    /**
     * Unregister MQTT client control in testing framework.
     *
     * @param reason reason of shutdown
     * @throws GRPCException on errors
     */
    public void shutdown(String reason) throws GRPCException {
    }
}