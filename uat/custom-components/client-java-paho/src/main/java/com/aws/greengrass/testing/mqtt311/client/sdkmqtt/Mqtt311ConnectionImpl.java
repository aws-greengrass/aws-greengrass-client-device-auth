/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt311.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of MQTT 3.1.1 connection based on AWS IoT SDK.
 */
public class Mqtt311ConnectionImpl implements MqttConnection {
    private static final Logger logger = LogManager.getLogger(Mqtt311ConnectionImpl.class);
    private static final String EXCEPTION_WHEN_CONNECTING = "Exception occurred during connect";

    private final AtomicBoolean isClosing = new AtomicBoolean();
    private final AtomicBoolean isConnected = new AtomicBoolean();
    private final IMqttClient mqttClient;
    private int connectionId = 0;


    /**
     * Creates a MQTT 3.1.1 connection.
     *
     * @param connectionParams the connection parameters
     * @throws org.eclipse.paho.client.mqttv3.MqttException on errors
     */
    public Mqtt311ConnectionImpl(@NonNull MqttLib.ConnectionParams connectionParams)
            throws org.eclipse.paho.client.mqttv3.MqttException {
        super();
        this.mqttClient = createClient(connectionParams);
    }

    @Override
    public ConnectResult start(long timeout, int connectionId) throws MqttException {
        this.connectionId = connectionId;
        try {
            IMqttToken token = mqttClient.connectWithResult(new MqttConnectOptions());
            token.waitForCompletion();
            isConnected.set(true);
            logger.atInfo().log("MQTT 3.1.1 connection {} is establisted", connectionId);
            return buildConnectResult(true, token.isComplete());
        } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
            logger.atError().withThrowable(e).log(EXCEPTION_WHEN_CONNECTING);
            throw new MqttException(EXCEPTION_WHEN_CONNECTING, e);
        }
    }

    @Override
    public void disconnect(long timeout, int reasonCode) throws MqttException {
        if (!isClosing.getAndSet(true)) {
            try {
                disconnectAndClose();
            } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
                throw new MqttException("Could not disconnect", e);
            }
        }
    }

    /**
     * Creates a MQTT 311 connection.
     *
     * @param connectionParams connection parameters
     * @return MQTT 3.1.1 connection
     * @throws org.eclipse.paho.client.mqttv3.MqttException on errors
     */
    private IMqttClient createClient(MqttLib.ConnectionParams connectionParams)
            throws org.eclipse.paho.client.mqttv3.MqttException {
        return new MqttClient(connectionParams.getHost(), connectionParams.getClientId());
    }

    private void disconnectAndClose() throws org.eclipse.paho.client.mqttv3.MqttException {
        try {
            mqttClient.disconnect();
            logger.atInfo().log("MQTT 3.1.1 connection {} has been disconnected", connectionId);
        } finally {
            mqttClient.close();
        }
    }

    private static ConnectResult buildConnectResult(boolean success, Boolean sessionPresent) {
        ConnAckInfo connAckInfo = new ConnAckInfo(sessionPresent);
        return new ConnectResult(success, connAckInfo, null);
    }
}
