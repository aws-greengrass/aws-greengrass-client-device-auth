/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Properties;
import com.aws.greengrass.testing.mqtt5.client.GRPCClient;
import com.aws.greengrass.testing.mqtt5.client.GRPCClient.DisconnectInfo;
import com.aws.greengrass.testing.mqtt5.client.GRPCClient.MqttReceivedMessage;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.mqtt5.Mqtt5Client;
import software.amazon.awssdk.crt.mqtt5.Mqtt5ClientOptions;
import software.amazon.awssdk.crt.mqtt5.Mqtt5ClientOptions.ClientSessionBehavior;
import software.amazon.awssdk.crt.mqtt5.NegotiatedSettings;
import software.amazon.awssdk.crt.mqtt5.OnAttemptingConnectReturn;
import software.amazon.awssdk.crt.mqtt5.OnConnectionFailureReturn;
import software.amazon.awssdk.crt.mqtt5.OnConnectionSuccessReturn;
import software.amazon.awssdk.crt.mqtt5.OnDisconnectionReturn;
import software.amazon.awssdk.crt.mqtt5.OnStoppedReturn;
import software.amazon.awssdk.crt.mqtt5.PublishResult;
import software.amazon.awssdk.crt.mqtt5.PublishReturn;
import software.amazon.awssdk.crt.mqtt5.QOS;
import software.amazon.awssdk.crt.mqtt5.packets.ConnAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.ConnectPacket;
import software.amazon.awssdk.crt.mqtt5.packets.DisconnectPacket;
import software.amazon.awssdk.crt.mqtt5.packets.PubAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.PublishPacket;
import software.amazon.awssdk.crt.mqtt5.packets.SubAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.SubscribePacket;
import software.amazon.awssdk.crt.mqtt5.packets.UnsubAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.UnsubscribePacket;
import software.amazon.awssdk.crt.mqtt5.packets.UserProperty;
import software.amazon.awssdk.iot.AwsIotMqtt5ClientBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Implementation of MQTT5 connection based on AWS IoT device SDK.
 */
public class MqttConnectionImpl implements MqttConnection {
    private static final Logger logger = LogManager.getLogger(MqttConnectionImpl.class);

    private final AtomicBoolean isClosing = new AtomicBoolean();
    private final AtomicBoolean isConnected = new AtomicBoolean();

    private final GRPCClient grpcClient;
    private final Mqtt5Client client;
    private int connectionId = 0;

    final ClientsLifecycleEvents lifecycleEvents = new ClientsLifecycleEvents();
    private final ClientsPublishEvents publishEvents = new ClientsPublishEvents();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();        // TODO: use DI

    private static class OnConnectionDoneInfo {
        final OnConnectionSuccessReturn onConnectionSuccessReturn;
        final OnConnectionFailureReturn onConnectionFailureReturn;
        final String crtError;

        @SuppressWarnings("PMD.NullAssignment")
        OnConnectionDoneInfo(OnConnectionSuccessReturn onConnectionSuccessReturn) {
            super();
            this.onConnectionSuccessReturn = onConnectionSuccessReturn;
            this.onConnectionFailureReturn = null;
            this.crtError = null;
        }

        @SuppressWarnings("PMD.NullAssignment")
        OnConnectionDoneInfo(OnConnectionFailureReturn onConnectionFailureReturn, String crtError) {
            super();
            this.onConnectionSuccessReturn = null;
            this.onConnectionFailureReturn = onConnectionFailureReturn;
            this.crtError = crtError;
        }
    }

    class ClientsLifecycleEvents implements Mqtt5ClientOptions.LifecycleEvents {
        CompletableFuture<OnConnectionDoneInfo> connectedFuture = new CompletableFuture<>();
        CompletableFuture<Void> stoppedFuture = new CompletableFuture<>();

        @Override
        public void onAttemptingConnect(Mqtt5Client client, OnAttemptingConnectReturn onAttemptingConnectReturn) {
            // OnAttemptingConnectReturn currently has no info
            logger.atInfo().log("MQTT connectionId {} connecting...", connectionId);
        }

        @Override
        public void onConnectionSuccess(Mqtt5Client client, OnConnectionSuccessReturn onConnectionSuccessReturn) {
            if (onConnectionSuccessReturn != null) {
                NegotiatedSettings negotiatedSettings = onConnectionSuccessReturn.getNegotiatedSettings();
                if (negotiatedSettings != null) {
                    String clientId = negotiatedSettings.getAssignedClientID();
                    logger.atInfo().log("MQTT connectionId {} connected, client id {}", connectionId, clientId);
                }
            }

            isConnected.set(true);
            connectedFuture.complete(new OnConnectionDoneInfo(onConnectionSuccessReturn));
        }

        @Override
        public void onConnectionFailure(Mqtt5Client client, OnConnectionFailureReturn onConnectionFailureReturn) {
            String errorString = CRT.awsErrorString(onConnectionFailureReturn.getErrorCode());
            logger.atInfo().log("MQTT connectionId {} failed with error: {}", connectionId, errorString);
            connectedFuture.complete(new OnConnectionDoneInfo(onConnectionFailureReturn, errorString));
        }

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        @Override
        public void onDisconnection(Mqtt5Client client, OnDisconnectionReturn onDisconnectionReturn) {
            isConnected.set(false);
            DisconnectPacket disconnectPacket = onDisconnectionReturn.getDisconnectPacket();
            String errorString = CRT.awsErrorString(onDisconnectionReturn.getErrorCode());

            // only unsolicited disconnect
            if (!isClosing.get()) {
                DisconnectInfo disconnectInfo = convertDisconnectPacket(disconnectPacket);
                executorService.submit(() -> {
                    try {
                        grpcClient.onMqttDisconnect(connectionId, disconnectInfo, errorString);
                    } catch (Exception ex) {
                        logger.atError().withThrowable(ex).log("onMqttDisconnect failed");
                    }
                });
            }

            logger.atInfo().log("MQTT connectionId {} disconnected error '{}' disconnectPacket '{}'",
                                connectionId, errorString, disconnectPacket);
        }

        @Override
        public void onStopped(Mqtt5Client client, OnStoppedReturn onStoppedReturn) {
            // OnStoppedReturn currently has no info
            logger.atInfo().log("MQTT connectionId {} stopped", connectionId);
            stoppedFuture.complete(null);
        }
    }

    private class ClientsPublishEvents implements Mqtt5ClientOptions.PublishEvents {

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        @Override
        public void onMessageReceived(Mqtt5Client client, PublishReturn result) {
            PublishPacket packet = result.getPublishPacket();
            if (packet != null) {
                final int qos = packet.getQOS().getValue();
                final String topic = packet.getTopic();
                final boolean isRetain = packet.getRetain();

                final List<Mqtt5Properties> userProperties = convertToMqtt5Properties(packet.getUserProperties(),
                                                                                        "Received");

                final String contentType = packet.getContentType();
                if (contentType != null) {
                    logger.atInfo().log("RX content type: '{}'", contentType);
                }

                Boolean outgoingPayloadFormat = null;
                final PublishPacket.PayloadFormatIndicator payloadFormat = packet.getPayloadFormat();
                if (payloadFormat != null) {
                    outgoingPayloadFormat = payloadFormat == PublishPacket.PayloadFormatIndicator.UTF8;
                    logger.atInfo().log("RX payload format indicator: {}", outgoingPayloadFormat);
                }

                final Long messageExpiryInterval = packet.getMessageExpiryIntervalSeconds();
                if (messageExpiryInterval != null) {
                    logger.atInfo().log("RX message expiry interval: {}", messageExpiryInterval);
                }

                MqttReceivedMessage message = new MqttReceivedMessage(qos, isRetain, topic, packet.getPayload(),
                        userProperties, contentType, outgoingPayloadFormat, messageExpiryInterval);
                executorService.submit(() -> {
                    try {
                        grpcClient.onReceiveMqttMessage(connectionId, message);
                    } catch (Exception ex) {
                        logger.atError().withThrowable(ex).log("onReceiveMqttMessage failed");
                    }
                });

                logger.atInfo().log("Received MQTT message: connectionId {} topic {} QoS {} retain {}",
                                        connectionId, topic, qos, isRetain);
            }
        }
    }

    /**
     * Creates a MQTT5 connection.
     *
     * @param connectionParams the connection parameters
     * @param grpcClient the consumer of received messages and disconnect events
     * @throws MqttException on errors
     */
    public MqttConnectionImpl(@NonNull MqttLib.ConnectionParams connectionParams, @NonNull GRPCClient grpcClient)
                    throws MqttException {
        super();

        this.grpcClient = grpcClient;
        this.client = createClient(connectionParams);
    }

    /**
     * Creates a MQTT5 connection for tests.
     *
     * @param grpcClient the consumer of received messages and disconnect events
     * @param client the client backend
     * @throws MqttException on errors
     */
    MqttConnectionImpl(@NonNull GRPCClient grpcClient, @NonNull Mqtt5Client client) {
        super();
        this.grpcClient = grpcClient;
        this.client = client;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public ConnectResult start(long timeout, int connectionId) throws MqttException {
        this.connectionId = connectionId;
        client.start();
        try {
            OnConnectionDoneInfo onConnectionDoneInfo = lifecycleEvents.connectedFuture.get(timeout, TimeUnit.SECONDS);
            // translate onConnectionInfo to intermediate object and return
            final boolean success = onConnectionDoneInfo.onConnectionSuccessReturn != null;
            ConnAckPacket packet = null;
            if (success) {
                packet = onConnectionDoneInfo.onConnectionSuccessReturn.getConnAckPacket();
            } else {
                if (onConnectionDoneInfo.onConnectionFailureReturn != null) {
                    packet = onConnectionDoneInfo.onConnectionFailureReturn.getConnAckPacket();
                }
            }
            return buildConnectResult(success, packet, onConnectionDoneInfo.crtError);
        } catch (Exception ex) {
            logger.atError().withThrowable(ex).log("Exception occurred during connect");
            throw new MqttException("Exception occurred during connect", ex);
        }
    }

    @SuppressWarnings({"PMD.UseTryWithResources", "PMD.AvoidCatchingGenericException"})
    @Override
    public void disconnect(long timeout, int reasonCode, List<Mqtt5Properties> userProperties) throws MqttException {

        if (!isClosing.getAndSet(true)) {
            final DisconnectPacket.DisconnectReasonCode disconnectReason
                    = DisconnectPacket.DisconnectReasonCode.getEnumValueFromInteger(reasonCode);
            DisconnectPacket.DisconnectPacketBuilder builder = new DisconnectPacket.DisconnectPacketBuilder()
                    .withReasonCode(disconnectReason);

            if (userProperties != null && !userProperties.isEmpty()) {
                builder.withUserProperties(convertToUserProperties(userProperties, "TX DISCONNECT"));
            }

            client.stop(builder.build());

            try {
                final long deadline = System.nanoTime() + timeout * 1_000_000_000;
                lifecycleEvents.stoppedFuture.get(timeout, TimeUnit.SECONDS);

                long remaining = deadline - System.nanoTime();
                if (remaining < MIN_SHUTDOWN_NS) {
                    remaining = MIN_SHUTDOWN_NS;
                }

                executorService.shutdown();
                if (!executorService.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                logger.atError().withThrowable(ex).log("Failed during disconnecting from MQTT broker");
                throw new MqttException("Could not disconnect", ex);
            } finally {
                client.close();
            }
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public PubAckInfo publish(long timeout, final @NonNull Message message)
                    throws MqttException {

        stateCheck();

        QOS qosEnum = QOS.getEnumValueFromInteger(message.getQos());
        PublishPacket.PublishPacketBuilder publishPacket = new PublishPacket.PublishPacketBuilder()
                                            .withTopic(message.getTopic())
                                            .withQOS(qosEnum)
                                            .withRetain(message.isRetain())
                                            .withPayload(message.getPayload());

        /* TODO: use also
                    withResponseTopic()
                    withPayloadFormat()
                    withMessageExpiryIntervalSeconds()
                    withCorrelationData() - ???
        */

        final List<Mqtt5Properties> userProperties = message.getUserProperties();
        if (userProperties != null && !userProperties.isEmpty()) {
            publishPacket.withUserProperties(convertToUserProperties(userProperties, "TX PUBLISH"));
        }

        final String contentType = message.getContentType();
        if (contentType != null) {
            publishPacket.withContentType(contentType);
            logger.atInfo().log("Publish content type: '{}'", contentType);
        }

        final Boolean payloadFormatIndicator = message.getPayloadFormatIndicator();
        if (payloadFormatIndicator != null) {
            publishPacket.withPayloadFormat(payloadFormatIndicator
                    ? PublishPacket.PayloadFormatIndicator.UTF8 : PublishPacket.PayloadFormatIndicator.BYTES);
            logger.atInfo().log("Publish payload format indicator: {}", payloadFormatIndicator);
        }

        final Integer messageExpiryInterval = message.getMessageExpiryInterval();
        if (messageExpiryInterval != null) {
            publishPacket.withMessageExpiryIntervalSeconds(Long.valueOf(messageExpiryInterval));
            logger.atInfo().log("Publish message expiry interval: {}", messageExpiryInterval);
        }

        CompletableFuture<PublishResult> publishFuture = client.publish(publishPacket.build());
        try {
            PublishResult publishResult = publishFuture.get(timeout, TimeUnit.SECONDS);
            return convertPublishResult(publishResult);
        } catch (Exception ex) {
            logger.atError().withThrowable(ex).log("Failed during publishing message");
            throw new MqttException("Could not publish message", ex);
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public SubAckInfo subscribe(long timeout, Integer subscriptionId, List<Mqtt5Properties> userProperties,
                                final @NonNull List<Subscription> subscriptions)
            throws MqttException {

        stateCheck();

        SubscribePacket.SubscribePacketBuilder builder = new SubscribePacket.SubscribePacketBuilder();
        if (subscriptionId != null) {
            builder.withSubscriptionIdentifier(subscriptionId.longValue());
        }

        for (Subscription subscription : subscriptions) {
            QOS qosEnum = QOS.getEnumValueFromInteger(subscription.getQos());
            int retainHandling = subscription.getRetainHandling();
            SubscribePacket.RetainHandlingType handling
                = SubscribePacket.RetainHandlingType.getEnumValueFromInteger(retainHandling);
            builder.withSubscription(subscription.getFilter(), qosEnum, subscription.isNoLocal(),
                                    subscription.isRetainAsPublished(),
                                    handling);

        }

        if (userProperties != null && !userProperties.isEmpty()) {
            builder.withUserProperties(convertToUserProperties(userProperties, "SUBSCRIBE"));
        }

        CompletableFuture<SubAckPacket> subscribeFuture = client.subscribe(builder.build());
        try {
            SubAckPacket subAckPacket = subscribeFuture.get(timeout, TimeUnit.SECONDS);
            return convertSubAckPacket(subAckPacket);
        } catch (Exception ex) {
            logger.atError().withThrowable(ex).log("Failed during subscribe");
            throw new MqttException("Could not subscribe", ex);
        }
    }


    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public UnsubAckInfo unsubscribe(long timeout, List<Mqtt5Properties> userProperties,
                                    final @NonNull List<String> filters)
            throws MqttException {

        stateCheck();

        UnsubscribePacket.UnsubscribePacketBuilder builder = new UnsubscribePacket.UnsubscribePacketBuilder();

        if (userProperties != null && !userProperties.isEmpty()) {
            builder.withUserProperties(convertToUserProperties(userProperties, "UNSUBSCRIBE"));
        }
        filters.forEach(builder::withSubscription);

        CompletableFuture<UnsubAckPacket> unsubscribeFuture = client.unsubscribe(builder.build());
        try {
            UnsubAckPacket unsubAckPacket = unsubscribeFuture.get(timeout, TimeUnit.SECONDS);
            return convertUnsubAckPacket(unsubAckPacket);
        } catch (Exception ex) {
            logger.atError().withThrowable(ex).log("Failed during unsubscribe");
            throw new MqttException("Could not unsubscribe", ex);
        }
    }

    /**
     * Creates a MQTT5 client.
     *
     * @param connectionParams connection parameters
     */
    private Mqtt5Client createClient(MqttLib.ConnectionParams connectionParams) {

        try (AwsIotMqtt5ClientBuilder builder = getClientBuilder(connectionParams)) {
            ConnectPacket.ConnectPacketBuilder connectBuilder = new ConnectPacket.ConnectPacketBuilder()
                .withClientId(connectionParams.getClientId())
                .withKeepAliveIntervalSeconds(Long.valueOf(connectionParams.getKeepalive()));

            final List<Mqtt5Properties> userProperties = connectionParams.getUserProperties();
            if (userProperties != null && !userProperties.isEmpty()) {
                connectBuilder.withUserProperties(convertToUserProperties(userProperties, "CONNECT"));
            }

            ClientSessionBehavior clientSessionBehavior = connectionParams.isCleanSession()
                        ? ClientSessionBehavior.CLEAN : ClientSessionBehavior.DEFAULT;

            builder.withConnectProperties(connectBuilder)
                .withSessionBehavior(clientSessionBehavior)
                .withPort(Long.valueOf(connectionParams.getPort()))
                .withLifeCycleEvents(lifecycleEvents)
                .withPublishEvents(publishEvents);

            /* TODO: other options:
                withAckTimeoutSeconds()
                withConnackTimeoutMs()
                withExtendedValidationAndFlowControlOptions()
                withMaxReconnectDelayMs()
                withMaxReconnectDelayMs()
                withMinReconnectDelayMs()
                withOfflineQueueBehavior()
                withPingTimeoutMs()
                withRetryJitterMode()
                withSocketOptions()

            */

            return builder.build();
        }
    }

    /**
     * Creates a MQTT5 client builder.
     *
     * @param connectionParams connection parameters
     */
    private AwsIotMqtt5ClientBuilder getClientBuilder(MqttLib.ConnectionParams connectionParams) {
        if (connectionParams.getKey() == null) {
            // NOTE: after tests we found AWS IoT SDK for Java v2 does not support connections without TLS.
            logger.atInfo().log("Creating Mqtt5Client without TLS");
            return AwsIotMqtt5ClientBuilder.newMqttBuilder(connectionParams.getHost());
        } else {
            logger.atInfo().log("Creating Mqtt5Client with TLS");
            return AwsIotMqtt5ClientBuilder.newDirectMqttBuilderWithMtlsFromMemory(
                    connectionParams.getHost(),
                    connectionParams.getCert(),
                    connectionParams.getKey())
                .withCertificateAuthority(connectionParams.getCa());
        }
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

    private static ConnectResult buildConnectResult(boolean success, ConnAckPacket packet, String crtError) {
        ConnAckInfo connAckInfo = convertConnAckPacket(packet);
        return new ConnectResult(success, connAckInfo, crtError);
    }

    private static ConnAckInfo convertConnAckPacket(ConnAckPacket packet) {
        if (packet == null) {
            return null;
        }

        ConnAckPacket.ConnectReasonCode reasonCode = packet.getReasonCode();

        final List<Mqtt5Properties> userProperties = convertToMqtt5Properties(packet.getUserProperties(), "CONNACK");

        QOS maximumQOS = packet.getMaximumQOS();
        return new ConnAckInfo(packet.getSessionPresent(),
                                reasonCode == null ? null : reasonCode.getValue(),
                                convertLongToInteger(packet.getSessionExpiryInterval()),
                                packet.getReceiveMaximum(),
                                maximumQOS == null ? null : maximumQOS.getValue(),
                                packet.getRetainAvailable(),
                                convertLongToInteger(packet.getMaximumPacketSize()),
                                packet.getAssignedClientIdentifier(),
                                packet.getReasonString(),
                                packet.getWildcardSubscriptionsAvailable(),
                                packet.getSubscriptionIdentifiersAvailable(),
                                packet.getSharedSubscriptionsAvailable(),
                                packet.getServerKeepAlive(),
                                packet.getResponseInformation(),
                                packet.getServerReference(),
                                userProperties
                                );
    }

    private static Integer convertLongToInteger(Long value) {
        if (value == null) {
            return null;
        }
        return value.intValue();
    }

    private static SubAckInfo convertSubAckPacket(SubAckPacket packet) {
        if (packet == null) {
            return null;
        }

        List<Integer> resultCodes = null;
        List<SubAckPacket.SubAckReasonCode> codes = packet.getReasonCodes();
        if (codes != null) {
            resultCodes = codes.stream().map(c -> c == null ? null : c.getValue()).collect(Collectors.toList());
        }

        final List<Mqtt5Properties> userProperties = convertToMqtt5Properties(packet.getUserProperties(), "SUBACK");

        return new SubAckInfo(resultCodes, packet.getReasonString(), userProperties);
    }

    private static UnsubAckInfo convertUnsubAckPacket(UnsubAckPacket packet) {
        if (packet == null) {
            return null;
        }

        List<Integer> resultCodes = null;
        List<UnsubAckPacket.UnsubAckReasonCode> codes = packet.getReasonCodes();
        if (codes != null) {
            resultCodes = codes.stream().map(c -> c == null ? null : c.getValue()).collect(Collectors.toList());
        }

        final List<Mqtt5Properties> userProperties = convertToMqtt5Properties(packet.getUserProperties(), "UNSUBACK");

        return new UnsubAckInfo(resultCodes, packet.getReasonString(), userProperties);
    }

    private static PubAckInfo convertPublishResult(PublishResult publishResult) {
        if (publishResult == null || publishResult.getType() != PublishResult.PublishResultType.PUBACK) {
            return null;
        }
        PubAckPacket pubAckPacket = publishResult.getResultPubAck();
        if (pubAckPacket == null) {
            return null;
        }
        PubAckPacket.PubAckReasonCode reasonCode = pubAckPacket.getReasonCode();

        final List<Mqtt5Properties> userProperties = convertToMqtt5Properties(pubAckPacket.getUserProperties(),
                                                                                "PUBACK");

        return new PubAckInfo(reasonCode == null ? null : reasonCode.getValue(), pubAckPacket.getReasonString(),
                userProperties);
    }

    private static DisconnectInfo convertDisconnectPacket(DisconnectPacket packet) {
        if (packet == null) {
            return null;
        }

        final List<Mqtt5Properties> userProperties = convertToMqtt5Properties(packet.getUserProperties(),
                                                                                "RX DISCONNECT");
        final DisconnectPacket.DisconnectReasonCode reasonCode = packet.getReasonCode();

        return new DisconnectInfo(reasonCode == null ? null : reasonCode.getValue(),
                                    convertLongToInteger(packet.getSessionExpiryIntervalSeconds()),
                                    packet.getReasonString(),
                                    packet.getServerReference(),
                                    userProperties
                                    );
    }

    private List<UserProperty> convertToUserProperties(@NonNull List<Mqtt5Properties> properties,
                                                        @NonNull String type) {
        List<UserProperty> userProperties = new ArrayList<>();
        properties.forEach(p -> {
            userProperties.add(new UserProperty(p.getKey(), p.getValue()));
            logger.atInfo().log("{} Tx user property '{}':'{}'", type, p.getKey(), p.getValue());
        });

        return userProperties;
    }

    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
    private static List<Mqtt5Properties> convertToMqtt5Properties(List<UserProperty> properties, @NonNull String type) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }

        List<Mqtt5Properties> mqttUserProperties = new ArrayList<>();
        properties.forEach(p -> {
            mqttUserProperties.add(Mqtt5Properties.newBuilder().setKey(p.key).setValue(p.value).build());
            logger.atInfo().log("{} Rx user property '{}':'{}'", type, p.key, p.value);
        });

        return mqttUserProperties;
    }
}
