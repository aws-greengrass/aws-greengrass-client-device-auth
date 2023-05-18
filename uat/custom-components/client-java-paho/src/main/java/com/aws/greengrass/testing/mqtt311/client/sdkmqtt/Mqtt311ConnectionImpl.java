/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt311.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import com.aws.greengrass.testing.util.SslUtil;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocketFactory;

/**
 * Implementation of MQTT 3.1.1 connection based on AWS IoT SDK.
 */
public class Mqtt311ConnectionImpl implements MqttConnection {
    private static final Logger logger = LogManager.getLogger(Mqtt311ConnectionImpl.class);
    private static final String EXCEPTION_WHEN_CONNECTING = "Exception occurred during connect";
    private static final String EXCEPTION_WHEN_CONFIGURE_SSL_CA = "Exception occurred during SSL configuration";

    private final AtomicBoolean isClosing = new AtomicBoolean();
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
    public ConnectResult start(MqttLib.ConnectionParams connectionParams, int connectionId) throws MqttException {
        this.connectionId = connectionId;
        try {
            MqttConnectOptions connectOptions = convertParams(connectionParams);
            IMqttToken token = mqttClient.connectWithResult(connectOptions);
            token.waitForCompletion();
            logger.atInfo().log("MQTT 3.1.1 connection {} is establisted", connectionId);
            return buildConnectResult(true, token.isComplete());
        } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
            logger.atError().withThrowable(e).log(EXCEPTION_WHEN_CONNECTING);
            throw new MqttException(EXCEPTION_WHEN_CONNECTING, e);
        } catch (IOException | GeneralSecurityException e) {
            logger.atError().withThrowable(e).log(EXCEPTION_WHEN_CONFIGURE_SSL_CA);
            throw new MqttException(EXCEPTION_WHEN_CONFIGURE_SSL_CA, e);
        }
    }

    @Override
    public void disconnect(long timeout, int reasonCode) throws MqttException {
        if (!isClosing.getAndSet(true)) {
            try {
                disconnectAndClose(timeout);
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

    private MqttConnectOptions convertParams(MqttLib.ConnectionParams connectionParams)
            throws GeneralSecurityException, IOException {
        MqttConnectOptions connectionOptions = new MqttConnectOptions();
        connectionOptions.setServerURIs(new String[]{connectionParams.getHost()});
        connectionOptions.setConnectionTimeout(connectionParams.getConnectionTimeout());
        SSLSocketFactory sslSocketFactory = SslUtil.getSocketFactory(connectionParams);
        connectionOptions.setSocketFactory(sslSocketFactory);
        return connectionOptions;
    }

    private void disconnectAndClose(long timeout) throws org.eclipse.paho.client.mqttv3.MqttException {
        try {
            mqttClient.disconnectForcibly(timeout);
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
