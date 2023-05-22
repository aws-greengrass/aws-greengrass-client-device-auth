/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt311.client.paho;

import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
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
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
            token.waitForCompletion(connectionParams.getConnectionTimeout());
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
    public MqttSubscribeReply subscribe(long timeout, @NonNull List<Subscription> subscriptions) {
        String[] filters = new String[subscriptions.size()];
        int[] qos = new int[subscriptions.size()];
        for (int i = 0; i < subscriptions.size(); i++) {
            filters[i] = subscriptions.get(i).getFilter();
            qos[i] = subscriptions.get(i).getQos();
        }
        MqttSubscribeReply.Builder builder = MqttSubscribeReply.newBuilder();
        try {
            IMqttToken token = mqttClient.subscribeWithResponse(filters, qos);
            token.waitForCompletion(TimeUnit.SECONDS.toMillis(timeout));
            builder.addReasonCodes(0);
        } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
            builder.addReasonCodes(e.getReasonCode());
            builder.setReasonString(e.getMessage());
        }
        return builder.build();
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

    @Override
    public MqttPublishReply publish(long timeout, @NonNull Message message) {
        MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setQos(message.getQos());
        mqttMessage.setPayload(message.getPayload());
        mqttMessage.setRetained(message.isRetain());
        MqttPublishReply.Builder builder = MqttPublishReply.newBuilder();
        try {
            mqttClient.publish(message.getTopic(), mqttMessage);
            builder.setReasonCode(0);
        } catch (org.eclipse.paho.client.mqttv3.MqttException ex) {
            logger.atError().withThrowable(ex)
                    .log("Failed during publishing message with reasonCode {} and reasonString {}",
                            ex.getReasonCode(), ex.getMessage());
            builder.setReasonCode(ex.getReasonCode());
            builder.setReasonString(ex.getMessage());
        }
        return builder.build();
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
