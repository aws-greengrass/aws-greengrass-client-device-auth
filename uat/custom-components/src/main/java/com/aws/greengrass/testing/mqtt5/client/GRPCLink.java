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
     * Handle all gRPC requests received from control.
     *
     * @param mqttLib MQTT library
     * @return shutdown reason as received from control or null
     * @throws GRPCException on errors
     * @throws InterruptedException when thread has been interrupted
     */
    String handleRequests(MqttLib mqttLib) throws GRPCException, InterruptedException;

    /**
     * Unregister agent from control.
     *
     * @param reason reason of shutdown
     * @throws GRPCException on errors
     */
    void shutdown(String reason) throws GRPCException;
}
