/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.MqttReceivedMessage;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.mqtt5.Mqtt5Client;
import software.amazon.awssdk.crt.mqtt5.Mqtt5ClientOptions;
import software.amazon.awssdk.crt.mqtt5.Mqtt5ClientOptions.ClientSessionBehavior;
import software.amazon.awssdk.crt.mqtt5.OnAttemptingConnectReturn;
import software.amazon.awssdk.crt.mqtt5.OnConnectionFailureReturn;
import software.amazon.awssdk.crt.mqtt5.OnConnectionSuccessReturn;
import software.amazon.awssdk.crt.mqtt5.OnDisconnectionReturn;
import software.amazon.awssdk.crt.mqtt5.OnStoppedReturn;
import software.amazon.awssdk.crt.mqtt5.PublishResult;
import software.amazon.awssdk.crt.mqtt5.PublishReturn;
import software.amazon.awssdk.crt.mqtt5.QOS;
import software.amazon.awssdk.crt.mqtt5.packets.ConnectPacket;
import software.amazon.awssdk.crt.mqtt5.packets.DisconnectPacket;
import software.amazon.awssdk.crt.mqtt5.packets.PubAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.PublishPacket;
import software.amazon.awssdk.crt.mqtt5.packets.SubAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.SubscribePacket;
import software.amazon.awssdk.crt.mqtt5.packets.UnsubAckPacket;
import software.amazon.awssdk.crt.mqtt5.packets.UnsubscribePacket;
import software.amazon.awssdk.iot.AwsIotMqtt5ClientBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Implementation of MQTT5 connection.
 */
public class MqttConnectionImpl implements MqttConnection {
    private static final Logger logger = LogManager.getLogger(MqttConnectionImpl.class);

    private final AtomicBoolean isClosing = new AtomicBoolean();
    private final AtomicBoolean isConnected = new AtomicBoolean();

    private final BiConsumer<Integer, MqttReceivedMessage> messageConsumer;
    private final Mqtt5Client client;
    private int connectionId = 0;

    private final ClientsLifecycleEvents lifecycleEvents = new ClientsLifecycleEvents();
    private final ClientsPublishEvents publishEvents = new ClientsPublishEvents();

    private class ClientsLifecycleEvents implements Mqtt5ClientOptions.LifecycleEvents {
        CompletableFuture<Void> connectedFuture = new CompletableFuture<>();
        CompletableFuture<Void> stoppedFuture = new CompletableFuture<>();

        @Override
        public void onAttemptingConnect(Mqtt5Client client, OnAttemptingConnectReturn onAttemptingConnectReturn) {
            logger.atInfo().log("MQTT connectionId {} connecting...", connectionId);
        }

        @Override
        public void onConnectionSuccess(Mqtt5Client client, OnConnectionSuccessReturn onConnectionSuccessReturn) {
            String clientId = onConnectionSuccessReturn.getNegotiatedSettings().getAssignedClientID();
            logger.atInfo().log("MQTT connectionId {} connected, client id {}", connectionId, clientId);
            isConnected.set(true);
            connectedFuture.complete(null);
        }

        @Override
        public void onConnectionFailure(Mqtt5Client client, OnConnectionFailureReturn onConnectionFailureReturn) {
            String errorString = CRT.awsErrorString(onConnectionFailureReturn.getErrorCode());
            logger.atInfo().log("MQTT connectionId {} failed with error: {}", connectionId, errorString);
            connectedFuture.completeExceptionally(new MqttException("Could not connect: " + errorString));
        }

        @Override
        public void onDisconnection(Mqtt5Client client, OnDisconnectionReturn onDisconnectionReturn) {
            isConnected.set(false);
            logger.atInfo().log("MQTT connectionId {} disconnected", connectionId);
        }

        @Override
        public void onStopped(Mqtt5Client client, OnStoppedReturn onStoppedReturn) {
            logger.atInfo().log("MQTT connectionId {} stopped", connectionId);
            stoppedFuture.complete(null);
        }
    }

    private class ClientsPublishEvents implements Mqtt5ClientOptions.PublishEvents {
        @Override
        public void onMessageReceived(Mqtt5Client client, PublishReturn result) {
            PublishPacket packet = result.getPublishPacket();
            if (packet != null) {
                int qos = packet.getQOS().getValue();
                String topic = packet.getTopic();
                boolean isRetain = packet.getRetain();

                if (messageConsumer != null) {
                    MqttReceivedMessage message = MqttReceivedMessage.builder()
                                .qos(qos)
                                .retain(isRetain)
                                .topic(topic)
                            .payload(packet.getPayload())
                                .build();
                    messageConsumer.accept(connectionId, message);
                }

                logger.atInfo().log("Received MQTT message: connectionId {} topic {} QoS {} retain {}",
                                        connectionId, topic, qos, isRetain);
            }
        }
    }

    /**
     * Creates a MQTT5 connection.
     *
     * @param connectionParams connection parameters
     * @param messageConsumer consumer of received messages
     * @throws MqttException on errors
     */
    public MqttConnectionImpl(MqttLib.ConnectionParams connectionParams,
                                BiConsumer<Integer, MqttReceivedMessage> messageConsumer) throws MqttException {
        super();

        this.messageConsumer = messageConsumer;
        this.client = createClient(connectionParams);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public void start(long timeout, int connectionId) throws MqttException {
        this.connectionId = connectionId;
        client.start();
        try {
            lifecycleEvents.connectedFuture.get(timeout, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new MqttException("Exception occurred during connect", ex);
        }
    }

    @SuppressWarnings({"PMD.UseTryWithResources", "PMD.AvoidCatchingGenericException"})
    @Override
    public void disconnect(long timeout, int reasonCode) throws MqttException {

        if (!isClosing.getAndSet(true)) {
            DisconnectPacket.DisconnectReasonCode disconnectReason
                    = DisconnectPacket.DisconnectReasonCode.getEnumValueFromInteger(reasonCode);
            DisconnectPacket.DisconnectPacketBuilder disconnectBuilder = new DisconnectPacket.DisconnectPacketBuilder();
            DisconnectPacket disconnectPacket = disconnectBuilder.withReasonCode(disconnectReason).build();
            // TODO: use withUserProperties()
            client.stop(disconnectPacket);

            try {
                lifecycleEvents.stoppedFuture.get(timeout, TimeUnit.SECONDS);
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
    public PubAckInfo publish(long timeout, final Message message)
                    throws MqttException {

        stateCheck();

        /* TODO: use also
                    withUserProperties()
                    withResponseTopic()
                    withPayloadFormat()
                    withMessageExpiryIntervalSeconds()
                    withContentType()
                    withCorrelationData() - ???
        */
        QOS qosEnum = QOS.getEnumValueFromInteger(message.getQos());
        PublishPacket publishPacket = new PublishPacket.PublishPacketBuilder()
                                            .withTopic(message.getTopic())
                                            .withQOS(qosEnum)
                                            .withRetain(message.isRetain())
                                            .withPayload(message.getPayload())
                                            .build();
        CompletableFuture<PublishResult> publishFuture = client.publish(publishPacket);
        try {
            PublishResult result = publishFuture.get(timeout, TimeUnit.SECONDS);
            if (result == null || result.getType() != PublishResult.PublishResultType.PUBACK) {
                return null;
            }
            PubAckPacket pubAckPacket = result.getResultPubAck();
            // TODO: handler also user's properties of PUBACK
            return new PubAckInfo(pubAckPacket.getReasonCode().getValue(), pubAckPacket.getReasonString());
        } catch (Exception ex) {
            logger.atError().withThrowable(ex).log("Failed during publishing message");
            throw new MqttException("Could not publish message", ex);
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public SubAckInfo subscribe(long timeout, final Integer subscriptionId, final List<Subscription> subscriptions)
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

        CompletableFuture<SubAckPacket> subscribeFuture = client.subscribe(builder.build());
        try {
            SubAckPacket result = subscribeFuture.get(timeout, TimeUnit.SECONDS);
            if (result == null) {
                return null;
            }
            List<Integer> resultCodes = null;
            List<SubAckPacket.SubAckReasonCode> codes = result.getReasonCodes();
            if (codes != null) {
                resultCodes = codes.stream().map(code -> code.getValue()).collect(Collectors.toList());
            }
            // TODO: handler also user's properties of SUBACK
            return new SubAckInfo(resultCodes, result.getReasonString());
        } catch (Exception ex) {
            logger.atError().withThrowable(ex).log("Failed during subscribe");
            throw new MqttException("Could not subscribe", ex);
        }
    }


    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public UnsubAckInfo unsubscribe(long timeout, final List<String> filters)
            throws MqttException {

        stateCheck();

        UnsubscribePacket.UnsubscribePacketBuilder builder = new UnsubscribePacket.UnsubscribePacketBuilder();
        filters.stream().forEach(filter -> builder.withSubscription(filter));

        CompletableFuture<UnsubAckPacket> unsubscribeFuture = client.unsubscribe(builder.build());
        try {
            UnsubAckPacket result = unsubscribeFuture.get(timeout, TimeUnit.SECONDS);
            if (result == null) {
                return null;
            }
            List<Integer> resultCodes = null;
            List<UnsubAckPacket.UnsubAckReasonCode> codes = result.getReasonCodes();
            if (codes != null) {
                resultCodes = codes.stream().map(code -> code.getValue()).collect(Collectors.toList());
            }
            // TODO: handler also user's properties of UNSUBACK
            return new UnsubAckInfo(resultCodes, result.getReasonString());
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
            ConnectPacket.ConnectPacketBuilder connectProperties = new ConnectPacket.ConnectPacketBuilder()
                .withClientId(connectionParams.getClientId())
                .withKeepAliveIntervalSeconds(Long.valueOf(connectionParams.getKeepalive()));

            ClientSessionBehavior clientSessionBehavior = connectionParams.isCleanSession()
                        ? ClientSessionBehavior.CLEAN : ClientSessionBehavior.DEFAULT;

            builder.withConnectProperties(connectProperties)
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
}
