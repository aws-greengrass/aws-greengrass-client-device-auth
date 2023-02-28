/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;

/**
 * Interface of MQTT5 library.
 */
public class MqttLibImpl implements MqttLib {
    /**
     * Create a MQTT5 connection.
     *
     * @param clientId MQTT client id
     * @param host hostname or IP address of MQTT broker
     * @param port port of MQTT broker
     * @param keepalive keep alive interval
     * @param cleanSession clean session flag
     * @param ca CA content, can be null or empty
     * @param cert client's certificate content, can be null or empty
     * @param key client's key content, can be null or empty
     * @return MqttConnection on success
     * @throws MqttException on errors
     */
    public MqttConnection createConnection(String clientId, String host, int port, int keepalive, boolean cleanSession,
                                            String ca, String cert, String key) throws MqttException {
        return new MqttConnectionImpl(clientId, host, port, keepalive, cleanSession, ca, cert, key);
    }
}