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
    /**
     * Close MQTT connection.
     *
     * @param reasonCode reason why connection is closed
     * @throws MqttException on errors
     */
    void disconnect(short reasonCode) throws MqttException;
}
