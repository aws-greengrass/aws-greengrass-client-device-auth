/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt311.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.GRPCClient;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of MQTT 3.1.1 connection based on AWS IoT SDK.
 */
public class Mqtt311ConnectionImpl implements MqttConnection {
    private static final Logger logger = LogManager.getLogger(Mqtt311ConnectionImpl.class);
    private static final String EXCEPTION_WHEN_CONNECTING = "Exception occurred during connect";

    private final AtomicBoolean isClosing = new AtomicBoolean();
    private final AtomicBoolean isConnected = new AtomicBoolean();

    private final GRPCClient grpcClient;
    private final IMqttClient mqttClient;
    private int connectionId = 0;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();        // TODO: use DI

    /**
     * Creates a MQTT 3.1.1 connection.
     *
     * @param connectionParams the connection parameters
     * @param grpcClient the consumer of received messages and disconnect events
     * @throws MqttException on errors
     */
    public Mqtt311ConnectionImpl(@NonNull MqttLib.ConnectionParams connectionParams, @NonNull GRPCClient grpcClient)
            throws org.eclipse.paho.client.mqttv3.MqttException {
        super();
        this.grpcClient = grpcClient;
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
    public void disconnect(long timeout, int reasonCode) throws MqttException, org.eclipse.paho.client.mqttv3.MqttException {

        if (!isClosing.getAndSet(true)) {
            try {
                mqttClient.disconnect();

                logger.atInfo().log("MQTT 3.1.1 connection {} has been disconnected", connectionId);
            } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
                throw new RuntimeException(e);
            } finally {
                mqttClient.close();
            }
        }
    }

    /**
     * Creates a MQTT 311 connection.
     *
     * @param connectionParams connection parameters
     * @return MQTT 3.1.1 connection
     * @throws MqttException on errors
     */
    private IMqttClient createClient(MqttLib.ConnectionParams connectionParams) throws org.eclipse.paho.client.mqttv3.MqttException {
        IMqttClient client = new MqttClient(connectionParams.getHost(), connectionParams.getClientId());
        return client;
    }


    /**
     * Checks connection state.
     *
     * @throws MqttException when connection state is not allow opertation
     */
    private void stateCheck() throws MqttException {
        if (!isConnected.get()) {
            throw new MqttException("MQTT client is not in connected state");
        }

        if (isClosing.get()) {
            throw new MqttException("MQTT connection is closing");
        }
    }

    private static ConnectResult buildConnectResult(boolean success, Boolean sessionPresent) {
        ConnAckInfo connAckInfo = new ConnAckInfo(sessionPresent);
        return new ConnectResult(success, connAckInfo, null);
    }
}
