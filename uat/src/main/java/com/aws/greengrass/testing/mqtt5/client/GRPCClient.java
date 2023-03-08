/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;

/**
 * Interface to gRPC client.
 */
public interface GRPCClient {
    /**
     * Called when MQTT message is receive my MQTT client and deliver information from it to gRPC server.
     *
     * @param connectionId connection id which receives the message
     * @param message information from the received MQTT message
     * @throws GRPCException on errors
     */
    void onReceiveMqttMessage(int connectionId, MqttReceivedMessage message) throws GRPCException;
}
