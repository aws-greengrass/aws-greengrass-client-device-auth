/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;

/**
 * Interface of MQTT5 connection.
 */
public class MqttConnectionImpl implements MqttConnection {
    /**
     * Creates a MQTT5 connection.
     *
     * @param connectionRequest connect arguments
     * @throws MqttException on errors
     */
    @SuppressWarnings("PMD.UnusedFormalParameter") // TODO: remove
    public MqttConnectionImpl(MqttLib.ConnectRequest connectionRequest) throws MqttException {
    }

    /**
     * Close MQTT connection.
     *
     * @param reasonCode reason why connection is closed
     */
    @Override
    public void disconnect(int reasonCode) {
    }
}
