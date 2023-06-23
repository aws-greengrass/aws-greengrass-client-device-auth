/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.paho;

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
import org.eclipse.paho.mqttv5.client.IMqttAsyncClient;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.net.ssl.SSLSocketFactory;

/**
 * Implementation of MQTT5 connection based on AWS IoT device SDK.
 */
public class MqttConnectionImpl implements MqttConnection {
    private static final Logger logger = LogManager.getLogger(MqttConnectionImpl.class);

    private final AtomicBoolean isClosing = new AtomicBoolean();
    private final GRPCClient grpcClient;
    private final IMqttAsyncClient client;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private int connectionId = 0;
    static final int REASON_CODE_SUCCESS = 0;
    private static final long QUIESCE_TIMEOUT = 30_000L;

    /**
     * Creates a MQTT5 connection.
     *
     * @param connectionParams the connection parameters
     * @param grpcClient the consumer of received messages and disconnect events
     * @throws org.eclipse.paho.mqttv5.common.MqttException on errors
     */
    public MqttConnectionImpl(@NonNull MqttLib.ConnectionParams connectionParams, GRPCClient grpcClient)
            throws org.eclipse.paho.mqttv5.common.MqttException {
        super();

        this.client = createAsyncClient(connectionParams);
        this.grpcClient = grpcClient;
    }

    @Override
    public ConnectResult start(MqttLib.ConnectionParams connectionParams, int connectionId) throws MqttException {
        this.connectionId = connectionId;
        try {
            MqttConnectionOptions connectionOptions = convertConnectParams(connectionParams);
            IMqttToken token = client.connect(connectionOptions);
            token.waitForCompletion(TimeUnit.SECONDS.toMillis(connectionParams.getConnectionTimeout()));
            return buildConnectResult(true, token, null);
        } catch (org.eclipse.paho.mqttv5.common.MqttException ex) {
            logger.atError().withThrowable(ex).log("Exception occurred during connect reason code {}",
                    ex.getReasonCode());
            return buildConnectResult(false, null, ex.getMessage());
        } catch (IOException | GeneralSecurityException ex) {
            logger.atError().withThrowable(ex).log("Exception occurred during connect");
            throw new MqttException("Exception occurred during connect", ex);
        }
    }

    @Override
    public MqttSubscribeReply subscribe(long timeout, @NonNull List<Subscription> subscriptions,
                                        List<Mqtt5Properties>  userProperties) {
        MqttSubscription[] mqttSubscriptions = new MqttSubscription[subscriptions.size()];
        MqttMessageListener[] listeners = new MqttMessageListener[subscriptions.size()];
        for (int i = 0; i < subscriptions.size(); i++) {
            MqttSubscription subscription = new MqttSubscription(
                    subscriptions.get(i).getFilter(), subscriptions.get(i).getQos());
            subscription.setRetainHandling(subscriptions.get(i).getRetainHandling());
            subscription.setRetainAsPublished(subscriptions.get(i).isRetainAsPublished());
            mqttSubscriptions[i] = subscription;
            listeners[i] = new MqttMessageListener();
        }

        MqttProperties properties = new MqttProperties();
        if (userProperties != null && !userProperties.isEmpty()) {
            properties.setUserProperties(convertToUserProperties(userProperties));
            userProperties.forEach(p -> logger.atInfo()
                    .log("Subscribe MQTT userProperties: {}, {}", p.getKey(), p.getValue()));
        }

        MqttSubscribeReply.Builder builder = MqttSubscribeReply.newBuilder();
        try {
            IMqttToken response = client.subscribe(mqttSubscriptions, null, null, listeners, properties);
            response.waitForCompletion(TimeUnit.SECONDS.toMillis(timeout));
            List<Integer> reasonCodes = Arrays.stream(response.getReasonCodes()).boxed().collect(Collectors.toList());
            builder.addAllReasonCodes(reasonCodes);
            MqttProperties responseProps = response.getResponseProperties();
            if (responseProps != null) {
                if (responseProps.getReasonString() != null) {
                    builder.setReasonString(responseProps.getReasonString());
                }

                List<Mqtt5Properties> ackUserProperties =
                        getAckUserProperties(responseProps.getUserProperties(), "SubAck");
                if (ackUserProperties != null) {
                    builder.addAllProperties(ackUserProperties);
                }
            }
        } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
            builder.addReasonCodes(e.getReasonCode());
            builder.setReasonString(e.getMessage());
        }
        return builder.build();
    }

    @Override
    public void disconnect(long timeout, int reasonCode, List<Mqtt5Properties> userProperties) throws MqttException {
        if (!isClosing.getAndSet(true)) {
            try {
                disconnectAndClose(timeout, reasonCode, userProperties);
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

        MqttProperties properties = new MqttProperties();
        if (message.getUserProperties() != null && !message.getUserProperties().isEmpty()) {
            List<UserProperty> userProperties = convertToUserProperties(message.getUserProperties());
            properties.setUserProperties(userProperties);
            userProperties.forEach(p -> logger.atInfo()
                    .log("Publish MQTT userProperties: {}, {}", p.getKey(), p.getValue()));
        }

        String contentType = message.getContentType();
        if (contentType != null && !contentType.isEmpty()) {
            properties.setContentType(contentType);
            logger.atInfo().log("Publish MQTT payload 'content type' {}", contentType);
        }

        mqttMessage.setProperties(properties);

        MqttPublishReply.Builder builder = MqttPublishReply.newBuilder();
        try {
            IMqttToken response = client.publish(message.getTopic(), mqttMessage);
            response.waitForCompletion(TimeUnit.SECONDS.toMillis(timeout));

            if (response.getReasonCodes() != null && response.getReasonCodes().length > 0) {
                builder.setReasonCode(response.getReasonCodes()[0]);
            } else {
                builder.setReasonCode(REASON_CODE_SUCCESS);
            }

            MqttProperties responseProps = response.getResponseProperties();
            if (responseProps != null) {
                if (responseProps.getReasonString() != null) {
                    builder.setReasonString(responseProps.getReasonString());
                }

                List<Mqtt5Properties> ackUserProperties =
                        getAckUserProperties(responseProps.getUserProperties(), "PubAck");
                if (ackUserProperties != null) {
                    builder.addAllProperties(ackUserProperties);
                }
                responseProps.getContentType();
            }
        } catch (org.eclipse.paho.mqttv5.common.MqttException ex) {
            logger.atError().withThrowable(ex)
                    .log("Failed during publishing message with reasonCode {} and reasonString {}",
                            ex.getReasonCode(), ex.getMessage());
            builder.setReasonCode(ex.getReasonCode());
            builder.setReasonString(ex.getMessage());
        }
        return builder.build();
    }

    @Override
    public MqttSubscribeReply unsubscribe(long timeout, @NonNull List<String> filters,
                                          List<Mqtt5Properties> userProperties) {
        String[] filterArray = new String[filters.size()];
        for (int i = 0; i < filters.size(); i++) {
            filterArray[i] = filters.get(i);
        }

        MqttProperties properties = new MqttProperties();
        if (userProperties != null && !userProperties.isEmpty()) {
            properties.setUserProperties(convertToUserProperties(userProperties));
            userProperties.forEach(p -> logger.atInfo()
                    .log("Unsubscribe MQTT userProperties: {}, {}", p.getKey(), p.getValue()));
        }

        MqttSubscribeReply.Builder builder = MqttSubscribeReply.newBuilder();
        try {
            IMqttToken token = client.unsubscribe(filterArray, null, null, properties);
            token.waitForCompletion(TimeUnit.SECONDS.toMillis(timeout));

            int[] reasonCodes = token.getReasonCodes();
            if (reasonCodes.length > 0) {
                List<Integer> reasonCodeList = new ArrayList<>();
                for (int reasonCode : reasonCodes) {
                    reasonCodeList.add(reasonCode);
                }
                builder.addAllReasonCodes(reasonCodeList);
            }

            if (token.getResponseProperties() != null) {
                if (token.getResponseProperties().getReasonString() != null) {
                    builder.setReasonString(token.getResponseProperties().getReasonString());
                }

                List<Mqtt5Properties> ackUserProperties =
                        getAckUserProperties(token.getResponseProperties().getUserProperties(), "UnsubAck");
                if (ackUserProperties != null) {
                    builder.addAllProperties(ackUserProperties);
                }
            }
        } catch (org.eclipse.paho.mqttv5.common.MqttException e) {
            logger.atError().withThrowable(e).log("Failed during unsubscribe");
            builder.addReasonCodes(e.getReasonCode());
            builder.setReasonString(e.getMessage());
        }
        return builder.build();
    }

    /**
     * Creates a MQTT5 client.
     *
     * @param connectionParams connection parameters
     */
    private IMqttAsyncClient createAsyncClient(MqttLib.ConnectionParams connectionParams)
            throws org.eclipse.paho.mqttv5.common.MqttException {
        return new MqttAsyncClient(connectionParams.getHost(),
                connectionParams.getClientId());
    }

    private void disconnectAndClose(long timeout, int reasonCode, List<Mqtt5Properties> userProperties)
            throws org.eclipse.paho.mqttv5.common.MqttException {
        MqttProperties properties = new MqttProperties();
        if (userProperties != null && !userProperties.isEmpty()) {
            properties.setUserProperties(convertToUserProperties(userProperties));
            userProperties.forEach(p -> logger.atInfo()
                    .log("Disconnect MQTT userProperties: {}, {}", p.getKey(), p.getValue()));
        }
        try {
            client.disconnectForcibly(QUIESCE_TIMEOUT, timeout, reasonCode, properties);
        } finally {
            client.close();
        }
    }

    private MqttConnectionOptions convertConnectParams(MqttLib.ConnectionParams connectionParams)
            throws IOException, GeneralSecurityException {
        MqttConnectionOptions connectionOptions = new MqttConnectionOptions();
        connectionOptions.setServerURIs(new String[]{connectionParams.getHost()});
        connectionOptions.setConnectionTimeout(connectionParams.getConnectionTimeout());
        SSLSocketFactory sslSocketFactory = SslUtil.getSocketFactory(connectionParams);
        connectionOptions.setSocketFactory(sslSocketFactory);

        List<Mqtt5Properties> userProperties = connectionParams.getUserProperties();
        if (userProperties != null && !userProperties.isEmpty()) {
            connectionOptions.setUserProperties(convertToUserProperties(userProperties));
            userProperties.forEach(p -> logger.atInfo()
                    .log("Connect MQTT userProperties: {}, {}", p.getKey(), p.getValue()));
        }

        return connectionOptions;
    }

    private static ConnectResult buildConnectResult(boolean success, IMqttToken token, String error) {
        ConnAckInfo connAckInfo = convertConnAckPacket(token);
        return new ConnectResult(success, connAckInfo, error);
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

        List<Mqtt5Properties> ackUserProperties =
                getAckUserProperties(token.getResponseProperties().getUserProperties(), "ConAck");

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
                token.getResponseProperties().getServerReference(),
                ackUserProperties
        );
    }

    private static Integer convertLongToInteger(Long value) {
        if (value == null) {
            return null;
        }
        return value.intValue();
    }

    private class MqttMessageListener implements IMqttMessageListener {
        @Override
        public void messageArrived(String topic, MqttMessage mqttMessage) {
            MqttProperties receivedProperties = mqttMessage.getProperties();
            String contentType = receivedProperties != null ? receivedProperties.getContentType() : null;

            List<Mqtt5Properties> userProps = convertToMqtt5Properties(receivedProperties);
            GRPCClient.MqttReceivedMessage message = new GRPCClient.MqttReceivedMessage(
                    mqttMessage.getQos(), mqttMessage.isRetained(), topic,
                    mqttMessage.getPayload(), userProps, contentType);
            executorService.submit(() -> {
                grpcClient.onReceiveMqttMessage(connectionId, message);
                logger.atInfo().log("Received MQTT message: connectionId {} topic {} QoS {} retain {}",
                        connectionId, topic, mqttMessage.getQos(), mqttMessage.isRetained());
            });
            if (userProps != null) {
                userProps.forEach(p -> logger.atInfo()
                        .log("Received MQTT userProperties: {}, {}", p.getKey(), p.getValue()));
            }
            if (contentType != null) {
                logger.atInfo().log("Received MQTT message has 'content type' {}", contentType);
            }
        }
    }

    private List<UserProperty> convertToUserProperties(List<Mqtt5Properties> properties) {
        List<UserProperty> userProperties = new ArrayList<>();
        properties.forEach(p ->  userProperties.add(new UserProperty(p.getKey(), p.getValue())));
        return userProperties;
    }

    private static List<Mqtt5Properties> convertToMqtt5Properties(MqttProperties properties) {
        List<Mqtt5Properties> userProps = null;
        if (properties == null || properties.getUserProperties() == null) {
            return userProps;
        }
        return convertToMqtt5Properties(properties.getUserProperties());
    }

    private static List<Mqtt5Properties> convertToMqtt5Properties(List<UserProperty> properties) {
        List<Mqtt5Properties> userProperties = new ArrayList<>();
        properties.forEach(p ->  userProperties.add(Mqtt5Properties.newBuilder()
                .setKey(p.getKey()).setValue(p.getValue()).build()));
        return userProperties;
    }

    private static List<Mqtt5Properties> getAckUserProperties(List<UserProperty> userPropertyList, String commandName) {
        List<Mqtt5Properties> userProperties = null;
        if (userPropertyList != null && !userPropertyList.isEmpty()) {
            userProperties = convertToMqtt5Properties(userPropertyList);
            userProperties.forEach(p -> logger.atInfo()
                    .log("{} MQTT userProperties: {}, {}", commandName, p.getKey(), p.getValue()));
        }
        return userProperties;
    }
}
