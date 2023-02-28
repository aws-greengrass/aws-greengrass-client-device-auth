/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;

/**
 * Interface of MQTT5 connection.
 */
public class MqttConnectionImpl implements MqttConnection {
    /**
     * Create a MQTT5 connection.
     *
     * @param clientId MQTT client id
     * @param host hostname of IP address of MQTT broker
     * @param port port of MQTT broker
     * @param keepalive keep alive interval
     * @param cleanSession clean session flag
     * @param ca pointer to CA content, can be NULL
     * @param cert pointer to client's certificate content, can be NULL
     * @param key pointer to client's key content, can be NULL
     * @throws MqttException on errors
     */
    public MqttConnectionImpl(String clientId, String host, int port, int keepalive, boolean cleanSession, String ca,
                                String cert, String key) throws MqttException {
    }

    /**
     * Close MQTT connection.
     *
     * @param reasonCode reason why connection is closed
     */
    public void disconnect(short reasonCode) {
    }
}