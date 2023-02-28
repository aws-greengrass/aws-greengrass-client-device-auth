/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt5.client.GRPCLib;
import com.aws.greengrass.testing.mqtt5.client.GRPCLink;
import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;

/**
 * Implementation of gRPC library.
 */
public class GRPCLibImpl implements GRPCLib {
    /**
     * Establish bidirectional link with the testing framework.
     *
     * @param agentId id of agent to identify control channel by gRPC server
     * @param host host name of gRPC server to connect to testing framework
     * @param port TCP port to connect to
     * @return connection handler
     * @throws GRPCException on errors
     */
    @Override
    public GRPCLink makeLink(String agentId, String host, int port) throws GRPCException {
        return new GRPCLinkImpl(agentId, host, port);
    }
}
