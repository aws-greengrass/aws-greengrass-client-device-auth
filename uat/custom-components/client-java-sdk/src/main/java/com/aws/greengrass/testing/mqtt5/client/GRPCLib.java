/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;
import lombok.NonNull;

/**
 * Interface of gRPC library.
 */
public interface GRPCLib {
    /**
     * Creates and establishes bidirectional link with the client control.
     *
     * @param agentId id of agent to identify control channel by control
     * @param host host name of gRPC server to connect to
     * @param port TCP port to connect to
     * @return connection handler
     * @throws GRPCException on errors
     */
    GRPCLink makeLink(@NonNull String agentId, @NonNull String host, int port) throws GRPCException;
}
