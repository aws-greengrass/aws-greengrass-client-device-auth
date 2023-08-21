/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;
import lombok.NonNull;

/**
 * Interface of gRPC bidirectional link.
 */
public interface GRPCLink {
    /**
     * Handle all gRPC requests received from control.
     *
     * @param mqttLib MQTT library
     * @param discoverClient the discover client
     * @return shutdown reason as received from control or null
     * @throws GRPCException on errors
     * @throws InterruptedException when thread has been interrupted
     */
    String handleRequests(@NonNull MqttLib mqttLib, @NonNull DiscoverClient discoverClient)
            throws GRPCException, InterruptedException;

    /**
     * Unregister agent from control.
     *
     * @param reason reason of shutdown
     * @throws GRPCException on errors
     */
    void shutdown(String reason) throws GRPCException;
}
