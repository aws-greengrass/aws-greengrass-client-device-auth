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
    @Override
    public GRPCLink makeLink(String agentId, String host, int port) throws GRPCException {
        return new GRPCLinkImpl(agentId, host, port);
    }
}
