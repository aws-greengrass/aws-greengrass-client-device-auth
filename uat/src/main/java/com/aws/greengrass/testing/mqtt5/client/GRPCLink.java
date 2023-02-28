/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;

/**
 * Interface of gRPC bidirectional link.
 */
public interface GRPCLink {
    /**
     * Handle gRPC requests.
     *
     * @param mqttLib MQTT library handler
     * @return shutdown reason as received from gRPC server
     * @throws GRPCException on errors
     */
    String handleRequests(MqttLib mqttLib) throws GRPCException;

    /**
     * Unregister MQTT client control in testing framework.
     *
     * @param reason reason of shutdown
     * @throws GRPCException on errors
     */
    void shutdown(String reason) throws GRPCException;
}
