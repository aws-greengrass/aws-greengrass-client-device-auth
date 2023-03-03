/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;

/**
 * Interface of MQTT5 connection.
 */
public interface MqttConnection {
    int DEFAULT_DISCONNECT_REASON = 4;
    long DEFAULT_DISCONNECT_TIMEOUT = 10;

    /**
     * Close MQTT connection.
     *
     * @param reasonCode reason why connection is closed
     * @param timeout disconnect operation timeout in seconds
     * @throws MqttException on errors
     */
    void disconnect(int reasonCode, long timeout) throws MqttException;


    /**
     * Publish MQTT message.
     *
     * @param retain if set message will retained
     * @param qos QoS value to publish message
     * @param timeout publish operation timeout in seconds
     * @param topic topic to publish message
     * @param content message content
     * @throws MqttException on errors
     */
    void publish(boolean retain, int qos, long timeout, String topic, byte[] content) throws MqttException;
}
