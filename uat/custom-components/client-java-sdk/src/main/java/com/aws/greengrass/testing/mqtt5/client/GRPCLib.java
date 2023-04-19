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
     * @param agentId the id of agent to identify control channel by control
     * @param hosts the array of host name or IP address of gRPC server to connect to
     * @param port the TCP port to connect to
     * @return connection handler
     * @throws GRPCException on errors
     */
    GRPCLink makeLink(@NonNull String agentId, @NonNull String[] hosts, int port) throws GRPCException;
}
