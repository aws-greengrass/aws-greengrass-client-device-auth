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

    /**
     * Close MQTT connection.
     *
     * @param reasonCode reason why connection is closed
     * @throws MqttException on errors
     */
    void disconnect(int reasonCode) throws MqttException;
}
