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
     * Creates a MQTT5 connection.
     *
     * @param connectionRequest connect arguments
     * @return MqttConnection on success
     * @throws MqttException on errors
     */
    @Override
    public MqttConnection createConnection(ConnectRequest connectionRequest) throws MqttException {
        return new MqttConnectionImpl(connectionRequest);
    }
}
