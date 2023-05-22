/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import com.aws.greengrass.testing.util.SslUtil;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.mqttv5.client.IMqttClient;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocketFactory;

/**
 * Implementation of MQTT5 connection based on AWS IoT device SDK.
 */
public class MqttConnectionImpl implements MqttConnection {
    private static final Logger logger = LogManager.getLogger(MqttConnectionImpl.class);

    private final AtomicBoolean isClosing = new AtomicBoolean();
    private final IMqttClient client;

    /**
     * Creates a MQTT5 connection.
     *
     * @param connectionParams the connection parameters
     * @throws org.eclipse.paho.mqttv5.common.MqttException on errors
     */
    public MqttConnectionImpl(@NonNull MqttLib.ConnectionParams connectionParams)
            throws org.eclipse.paho.mqttv5.common.MqttException {
        super();

        this.client = createClient(connectionParams);
    }

    @Override
    public ConnectResult start(MqttLib.ConnectionParams connectionParams, int connectionId) throws MqttException {
        try {
            MqttConnectionOptions connectionOptions = convertParams(connectionParams);
            IMqttToken token = client.connectWithResult(connectionOptions);
            token.waitForCompletion();
            return buildConnectResult(true, token);
        } catch (IOException | GeneralSecurityException | org.eclipse.paho.mqttv5.common.MqttException ex) {
            logger.atError().withThrowable(ex).log("Exception occurred during connect");
            throw new MqttException("Exception occurred during connect", ex);
        }
    }

    @Override
    public void disconnect(long timeout, int reasonCode) throws MqttException {
        if (!isClosing.getAndSet(true)) {
            try {
                disconnectAndClose(timeout);
            } catch (org.eclipse.paho.mqttv5.common.MqttException ex) {
                logger.atError().withThrowable(ex).log("Failed during disconnecting from MQTT broker");
                throw new MqttException("Could not disconnect", ex);
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
            client.publish(message.getTopic(), mqttMessage);
            builder.setReasonCode(0);
        } catch (org.eclipse.paho.mqttv5.common.MqttException ex) {
            logger.atError().withThrowable(ex)
                    .log("Failed during publishing message with reasonCode {} and reasonString {}",
                            ex.getReasonCode(), ex.getMessage());
            builder.setReasonCode(ex.getReasonCode());
            builder.setReasonString(ex.getMessage());
        }
        return builder.build();
    }

    /**
     * Creates a MQTT5 client.
     *
     * @param connectionParams connection parameters
     */
    private IMqttClient createClient(MqttLib.ConnectionParams connectionParams)
            throws org.eclipse.paho.mqttv5.common.MqttException {
        return new MqttClient(connectionParams.getHost(),
                connectionParams.getClientId(), new MemoryPersistence());
    }

    private void disconnectAndClose(long timeout) throws org.eclipse.paho.mqttv5.common.MqttException {
        try {
            client.disconnectForcibly(timeout);
        } finally {
            client.close();
        }
    }

    private MqttConnectionOptions convertParams(MqttLib.ConnectionParams connectionParams)
            throws IOException, GeneralSecurityException {
        MqttConnectionOptions connectionOptions = new MqttConnectionOptions();
        connectionOptions.setServerURIs(new String[]{connectionParams.getHost()});
        connectionOptions.setConnectionTimeout(connectionParams.getConnectionTimeout());
        SSLSocketFactory sslSocketFactory = SslUtil.getSocketFactory(connectionParams);
        connectionOptions.setSocketFactory(sslSocketFactory);

        return connectionOptions;
    }

    private static ConnectResult buildConnectResult(boolean success, IMqttToken token) {
        ConnAckInfo connAckInfo = convertConnAckPacket(token);
        return new ConnectResult(success, connAckInfo, null);
    }

    private static ConnAckInfo convertConnAckPacket(IMqttToken token) {
        if (token == null) {
            return null;
        }
        Integer sessionExpiryInterval = null;
        Boolean wildcardSubscriptionsAvailable = null;
        Boolean subscriptionIdentifiersAvailable = null;
        Boolean sharedSubscriptionAvailable = null;

        MqttProperties requestProperties = token.getRequestProperties();
        if (requestProperties != null) {
            sessionExpiryInterval = convertLongToInteger(requestProperties.getSessionExpiryInterval());
            wildcardSubscriptionsAvailable = requestProperties.isWildcardSubscriptionsAvailable();
            subscriptionIdentifiersAvailable = requestProperties.isSubscriptionIdentifiersAvailable();
            sharedSubscriptionAvailable = requestProperties.isSharedSubscriptionAvailable();
        }
        int[] reasonCodes = token.getReasonCodes();
        Integer reasonCode = reasonCodes == null ? null : reasonCodes[0];
        return new ConnAckInfo(token.getSessionPresent(),
                reasonCode,
                sessionExpiryInterval,
                token.getResponseProperties().getReceiveMaximum(),
                token.getResponseProperties().getMaximumQoS(),
                token.getResponseProperties().isRetainAvailable(),
                convertLongToInteger(token.getResponseProperties().getMaximumPacketSize()),
                token.getResponseProperties().getAssignedClientIdentifier(),
                token.getResponseProperties().getReasonString(),
                wildcardSubscriptionsAvailable,
                subscriptionIdentifiersAvailable,
                sharedSubscriptionAvailable,
                token.getResponseProperties().getServerKeepAlive(),
                token.getResponseProperties().getResponseInfo(),
                token.getResponseProperties().getServerReference()
        );
    }

    private static Integer convertLongToInteger(Long value) {
        if (value == null) {
            return null;
        }
        return value.intValue();
    }
}
