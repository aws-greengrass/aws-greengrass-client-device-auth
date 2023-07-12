/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt311.client.paho;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Properties;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import com.aws.greengrass.testing.mqtt5.client.GRPCClient;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import com.aws.greengrass.testing.util.SslUtil;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final int REASON_CODE_SUCCESS = 0;

    private final AtomicBoolean isClosing = new AtomicBoolean();
    private final IMqttAsyncClient mqttClient;
    private final GRPCClient grpcClient;
    private int connectionId = 0;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();


    /**
     * Creates a MQTT 3.1.1 connection.
     *
     * @param connectionParams the connection parameters
     * @param grpcClient the consumer of received messages and disconnect events
     * @throws org.eclipse.paho.client.mqttv3.MqttException on errors
     */
    public Mqtt311ConnectionImpl(@NonNull MqttLib.ConnectionParams connectionParams, GRPCClient grpcClient)
            throws org.eclipse.paho.client.mqttv3.MqttException {
        super();
        this.mqttClient = createAsyncClient(connectionParams);
        this.grpcClient = grpcClient;
    }

    @Override
    public ConnectResult start(MqttLib.ConnectionParams connectionParams, int connectionId) throws MqttException {
        checkUserProperties(connectionParams.getUserProperties());
        this.connectionId = connectionId;
        try {
            MqttConnectOptions connectOptions = convertParams(connectionParams);
            IMqttToken token = mqttClient.connect(connectOptions);
            token.waitForCompletion(TimeUnit.SECONDS.toMillis(connectionParams.getConnectionTimeout()));
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
    public MqttSubscribeReply subscribe(long timeout, @NonNull List<Subscription> subscriptions,
                                        List<Mqtt5Properties> userProperties) {
        checkUserProperties(userProperties);
        String[] filters = new String[subscriptions.size()];
        int[] qos = new int[subscriptions.size()];

        MqttMessageListener[] messageListeners = new MqttMessageListener[subscriptions.size()];
        for (int i = 0; i < subscriptions.size(); i++) {
            filters[i] = subscriptions.get(i).getFilter();
            qos[i] = subscriptions.get(i).getQos();
            messageListeners[i] = new MqttMessageListener();
        }
        MqttSubscribeReply.Builder builder = MqttSubscribeReply.newBuilder();
        try {
            IMqttToken token = mqttClient.subscribe(filters, qos, messageListeners);
            token.waitForCompletion(TimeUnit.SECONDS.toMillis(timeout));
            builder.addAllReasonCodes(Collections.nCopies(subscriptions.size(), REASON_CODE_SUCCESS));
        } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
            logger.atError().withThrowable(e).log("Exception occurred during subscribe, reason code {}",
                                                    e.getReasonCode());

            builder.addAllReasonCodes(Collections.nCopies(subscriptions.size(), (int)e.getReasonCode()));
        }
        return builder.build();
    }

    @Override
    public void disconnect(long timeout, int reasonCode, List<Mqtt5Properties> userProperties) throws MqttException {
        checkUserProperties(userProperties);
        if (isClosing.compareAndSet(false, true)) {
            try {
                disconnectAndClose(timeout);
            } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
                throw new MqttException("Could not disconnect", e);
            }
        }
    }

    @Override
    public MqttPublishReply publish(long timeout, @NonNull Message message) {
        checkUserProperties(message.getUserProperties());
        checkContentType(message.getContentType());
        checkPayloadFormatIndicator(message.getPayloadFormatIndicator());
        checkMessageExpiryInterval(message.getMessageExpiryInterval());
        checkResponseTopic(message.getResponseTopic());
        checkCorrelationData(message.getCorrelationData());

        MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setQos(message.getQos());
        mqttMessage.setPayload(message.getPayload());
        mqttMessage.setRetained(message.isRetain());
        MqttPublishReply.Builder builder = MqttPublishReply.newBuilder();
        try {
            IMqttDeliveryToken token = mqttClient.publish(message.getTopic(), mqttMessage);
            token.waitForCompletion(TimeUnit.SECONDS.toMillis(timeout));
            builder.setReasonCode(REASON_CODE_SUCCESS);
        } catch (org.eclipse.paho.client.mqttv3.MqttException ex) {
            logger.atError().withThrowable(ex)
                    .log("Failed during publishing message with reasonCode {} and reasonString {}",
                            ex.getReasonCode(), ex.getMessage());
            builder.setReasonCode(ex.getReasonCode());
        }
        return builder.build();
    }

    @Override
    public MqttSubscribeReply unsubscribe(long timeout, @NonNull List<String> filters,
                                          List<Mqtt5Properties> userProperties) {
        checkUserProperties(userProperties);

        MqttSubscribeReply.Builder builder = MqttSubscribeReply.newBuilder();
        try {
            IMqttToken token = mqttClient.unsubscribe(filters.toArray(new String[0]));
            token.waitForCompletion(TimeUnit.SECONDS.toMillis(timeout));
            builder.addAllReasonCodes(Collections.nCopies(filters.size(), REASON_CODE_SUCCESS));
        } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
            logger.atError().withThrowable(e).log("Exception occurred during unsubscribe, reason code {}",
                                                    e.getReasonCode());
            builder.addAllReasonCodes(Collections.nCopies(filters.size(), (int)e.getReasonCode()));
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
    private IMqttAsyncClient createAsyncClient(MqttLib.ConnectionParams connectionParams)
            throws org.eclipse.paho.client.mqttv3.MqttException {
        String uri = createUri(connectionParams.getHost(), connectionParams.getPort(),
                connectionParams.getCert() != null);
        return new MqttAsyncClient(uri, connectionParams.getClientId());
    }

    private MqttConnectOptions convertParams(MqttLib.ConnectionParams connectionParams)
            throws GeneralSecurityException, IOException {
        MqttConnectOptions connectionOptions = new MqttConnectOptions();

        if (connectionParams.getRequestResponseInformation() != null) {
            logger.atWarn().log("MQTT v3.1.1 does not support request response information");
        }

        String uri = createUri(connectionParams.getHost(), connectionParams.getPort(),
                connectionParams.getCert() != null);
        connectionOptions.setServerURIs(new String[]{uri});

        if (connectionParams.getKey() != null) {
            SSLSocketFactory sslSocketFactory = SslUtil.getSocketFactory(connectionParams);
            connectionOptions.setSocketFactory(sslSocketFactory);
        }

        connectionOptions.setConnectionTimeout(connectionParams.getConnectionTimeout());
        connectionOptions.setKeepAliveInterval(connectionParams.getKeepalive());
        connectionOptions.setCleanSession(connectionParams.isCleanSession());
        connectionOptions.setAutomaticReconnect(false);

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

    private class MqttMessageListener implements IMqttMessageListener {

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        @Override
        public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
            if (isClosing.get()) {
                logger.atWarn().log("PIBLISH event ignored due to shutdown initiated");
            } else {
                GRPCClient.MqttReceivedMessage message = new GRPCClient.MqttReceivedMessage(
                        mqttMessage.getQos(), mqttMessage.isRetained(), topic, mqttMessage.getPayload(),
                        null, null, null,null, null, null);
                executorService.submit(() -> {
                    try {
                        grpcClient.onReceiveMqttMessage(connectionId, message);
                    } catch (Exception ex) {
                        logger.atError().withThrowable(ex).log("onReceiveMqttMessage failed");
                    }
                });

                logger.atInfo().log("Received MQTT message: connectionId {} topic '{}' QoS {} retain {}",
                                        connectionId, topic, mqttMessage.getQos(), mqttMessage.isRetained());
            }
        }
    }

    private void checkUserProperties(List<Mqtt5Properties> userProperties) {
        if (userProperties != null && !userProperties.isEmpty()) {
            logger.warn("MQTT V3.1.1 doesn't support user properties");
        }
    }

    private void checkContentType(String contentType) {
        if (contentType != null && !contentType.isEmpty()) {
            logger.warn("MQTT V3.1.1 doesn't support 'content type'");
        }
    }

    private void checkPayloadFormatIndicator(Boolean payloadFormatIndicator) {
        if (payloadFormatIndicator != null) {
            logger.warn("MQTT V3.1.1 doesn't support 'payload format indicator'");
        }
    }

    private void checkMessageExpiryInterval(Integer messageExpiryInterval) {
        if (messageExpiryInterval != null) {
            logger.warn("MQTT V3.1.1 doesn't support 'message expiry interval'");
        }
    }

    private void checkResponseTopic(String responseTopic) {
        if (responseTopic != null) {
            logger.atWarn().log("MQTT v3.1.1 doesn't support response topic");
        }
    }

    private void checkCorrelationData(byte[] correlationData) {
        if (correlationData != null) {
            logger.atWarn().log("MQTT v3.1.1 doesn't support correlation data");
        }
    }
}
