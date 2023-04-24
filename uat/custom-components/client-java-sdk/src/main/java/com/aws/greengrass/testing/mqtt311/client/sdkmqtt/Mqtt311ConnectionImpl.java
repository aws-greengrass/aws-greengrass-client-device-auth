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
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of MQTT 3.1.1 connection based on AWS IoT SDK.
 */
public class Mqtt311ConnectionImpl implements MqttConnection {
    private static final Logger logger = LogManager.getLogger(Mqtt311ConnectionImpl.class);
    private static final String EXCEPTION_WHEN_CONNECTING = "Exception occurred during connect";
    private static final String EXCEPTION_WHEN_DISCONNECTING = "Exception occurred during disconnect";
    private static final String EXCEPTION_WHEN_PUBLISHING = "Exception occurred during publish";
    private static final String EXCEPTION_WHEN_SUBSCRIBING = "Exception occurred during subscribe";
    private static final String EXCEPTION_WHEN_UNSUBSCRIBING = "Exception occurred during unsubscribe";

    private static final int REASON_CODE_SUCCESS = 0;

    private final AtomicBoolean isClosing = new AtomicBoolean();
    private final AtomicBoolean isConnected = new AtomicBoolean();

    // TODO: remove when receive is implemented
    @SuppressWarnings("PMD.UnusedPrivateField")
    private final GRPCClient grpcClient;

    private final MqttClientConnection connection;

    private int connectionId = 0;

    @SuppressWarnings("PMD.UnusedPrivateField")
    private final ClientConnectionEvents connectionEvents = new ClientConnectionEvents();

    class ClientConnectionEvents implements MqttClientConnectionEvents {
        @Override
        public void onConnectionInterrupted(int errorCode) {
            isConnected.set(false);
            logger.atInfo().log("MQTT connection {} interrupted, error code {}", connectionId, errorCode);
        }

        @Override
        public void onConnectionResumed(boolean sessionPresent) {
            isConnected.set(true);
            logger.atInfo().log("MQTT connection {} resumed, sessionPresent {}", connectionId, sessionPresent);
        }
    }


    /**
     * Creates a MQTT 3.1.1 connection.
     *
     * @param connectionParams the connection parameters
     * @param grpcClient the consumer of received messages and disconnect events
     * @throws MqttException on errors
     */
    public Mqtt311ConnectionImpl(@NonNull MqttLib.ConnectionParams connectionParams, @NonNull GRPCClient grpcClient)
                    throws MqttException {
        super();
        this.grpcClient = grpcClient;
        this.connection = createConnection(connectionParams);
    }

    /**
     * Creates a MQTT 3.1.1 connection for tests.
     *
     * @param grpcClient the consumer of received messages and disconnect events
     * @param connection the connection backend
     * @throws MqttException on errors
     */
    Mqtt311ConnectionImpl(@NonNull GRPCClient grpcClient, @NonNull MqttClientConnection connection) {
        super();
        this.grpcClient = grpcClient;
        this.connection = connection;
    }

    @Override
    public ConnectResult start(long timeout, int connectionId) throws MqttException {
        this.connectionId = connectionId;

        CompletableFuture<Boolean> connectFuture = connection.connect();
        try {
            Boolean sessionPresent = connectFuture.get(timeout, TimeUnit.SECONDS);
            isConnected.set(true);
            logger.atInfo().log("MQTT 3.1.1 connection {} is establisted", connectionId);
            return buildConnectResult(true, sessionPresent);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.atError().withThrowable(ex).log(EXCEPTION_WHEN_CONNECTING);
            throw new MqttException(EXCEPTION_WHEN_CONNECTING, ex);
        } catch (TimeoutException | ExecutionException ex) {
            logger.atError().withThrowable(ex).log(EXCEPTION_WHEN_CONNECTING);
            throw new MqttException(EXCEPTION_WHEN_CONNECTING, ex);
        }
    }

    @Override
    public void disconnect(long timeout, int reasonCode) throws MqttException {

        if (!isClosing.getAndSet(true)) {
            CompletableFuture<Void> disconnnectFuture = connection.disconnect();
            try {
                disconnnectFuture.get(timeout, TimeUnit.SECONDS);
                logger.atInfo().log("MQTT 3.1.1 connection {} has been disconnected", connectionId);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.atError().withThrowable(ex).log(EXCEPTION_WHEN_DISCONNECTING);
                throw new MqttException(EXCEPTION_WHEN_DISCONNECTING, ex);
            } catch (TimeoutException | ExecutionException ex) {
                logger.atError().withThrowable(ex).log(EXCEPTION_WHEN_DISCONNECTING);
                throw new MqttException(EXCEPTION_WHEN_DISCONNECTING, ex);
            } finally {
                connection.close();
            }
        }
    }

    @Override
    public PubAckInfo publish(long timeout, final @NonNull Message message)
                    throws MqttException {

        stateCheck();

        final QualityOfService qos = QualityOfService.getEnumValueFromInteger(message.getQos());
        final String topic = message.getTopic();
        final MqttMessage msg = new MqttMessage(topic, message.getPayload(), qos, message.isRetain(), false);

        CompletableFuture<Integer> pubishFuture = connection.publish(msg);
        try {
            Integer packetId = pubishFuture.get(timeout, TimeUnit.SECONDS);
            logger.atInfo().log("Publish on connection {} to topic {} QoS {} packet Id {}",
                                    connectionId, topic, qos, packetId);
            return new PubAckInfo(REASON_CODE_SUCCESS, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.atError().withThrowable(ex).log(EXCEPTION_WHEN_PUBLISHING);
            throw new MqttException(EXCEPTION_WHEN_PUBLISHING, ex);
        } catch (TimeoutException | ExecutionException ex) {
            logger.atError().withThrowable(ex).log(EXCEPTION_WHEN_PUBLISHING);
            throw new MqttException(EXCEPTION_WHEN_PUBLISHING, ex);
        }
    }

    @Override
    public SubAckInfo subscribe(long timeout, Integer subscriptionId, final @NonNull List<Subscription> subscriptions)
            throws MqttException {

        stateCheck();

        if (subscriptionId != null) {
            throw new IllegalArgumentException("MQTT v3.1.1 doesn't support subscription id");
        }

        if (subscriptions.size() != 1) {
            throw new IllegalArgumentException("Iot device SDK MQTT v3.1.1 client does not support to subscribe on "
                                            + "multiple filters at once");
        }

        Subscription subscription = subscriptions.get(0);
        final String filter = subscription.getFilter();
        final QualityOfService qos = QualityOfService.getEnumValueFromInteger(subscription.getQos());

        CompletableFuture<Integer> subscribeFuture = connection.subscribe(filter, qos);
        try {
            Integer packetId = subscribeFuture.get(timeout, TimeUnit.SECONDS);
            logger.atInfo().log("Subscribed on connection {} for topics filter {} QoS {} packet Id {}",
                                    connectionId, filter, qos, packetId);
            return new SubAckInfo(Collections.singletonList(REASON_CODE_SUCCESS), null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.atError().withThrowable(ex).log(EXCEPTION_WHEN_SUBSCRIBING);
            throw new MqttException(EXCEPTION_WHEN_SUBSCRIBING, ex);
        } catch (TimeoutException | ExecutionException ex) {
            logger.atError().withThrowable(ex).log(EXCEPTION_WHEN_SUBSCRIBING);
            throw new MqttException(EXCEPTION_WHEN_SUBSCRIBING, ex);
        }
    }

    @Override
    public UnsubAckInfo unsubscribe(long timeout, final @NonNull List<String> filters)
            throws MqttException {

        stateCheck();

        if (filters.size() != 1) {
            throw new IllegalArgumentException("Iot device SDK MQTT v3.1.1 client does not support to unsubscribe from "
                                            + "multiple filters at once");
        }

        final String filter = filters.get(0);

        CompletableFuture<Integer> unsubscribeFuture = connection.unsubscribe(filter);
        try {
            Integer packetId = unsubscribeFuture.get(timeout, TimeUnit.SECONDS);
            logger.atInfo().log("Unsubscribed on connection {} from topics filter {} packet Id {}", connectionId,
                                    filter, packetId);
            return new UnsubAckInfo(Collections.singletonList(0), null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.atError().withThrowable(ex).log(EXCEPTION_WHEN_UNSUBSCRIBING);
            throw new MqttException(EXCEPTION_WHEN_UNSUBSCRIBING, ex);
        } catch (TimeoutException | ExecutionException ex) {
            logger.atError().withThrowable(ex).log(EXCEPTION_WHEN_UNSUBSCRIBING);
            throw new MqttException(EXCEPTION_WHEN_UNSUBSCRIBING, ex);
        }

    }

    /**
     * Creates a MQTT 311 connection.
     *
     * @param connectionParams connection parameters
     * @return MQTT 3.1.1 connection
     * @throws MqttException on errors
     */
    private MqttClientConnection createConnection(MqttLib.ConnectionParams connectionParams) throws MqttException {

        try (AwsIotMqttConnectionBuilder builder = getConnectionBuilder(connectionParams)) {
            builder.withEndpoint(connectionParams.getHost())
                .withPort((short)connectionParams.getPort())
                .withClientId(connectionParams.getClientId())
                .withCleanSession(connectionParams.isCleanSession())
                .withConnectionEventCallbacks(connectionEvents);

            /* TODO: other options:
                withKeepAliveMs() / withKeepAliveSecs()
                withPingTimeoutMs()
                withProtocolOperationTimeoutMs()
                withTimeoutMs()
                withReconnectTimeoutSecs()
                withSocketOptions()
                withUsername()
                withPassword()
                withWill(...)
                withBootstrap()
                withHttpProxyOptions()
            */

            return builder.build();
        }
    }

    /**
     * Creates a MQTT 3.1.1 connection builder.
     *
     * @param connectionParams connection parameters
     * @return builder of MQTT v3.1.1 connection
     * @throws MqttException on errors
     */
    private AwsIotMqttConnectionBuilder getConnectionBuilder(MqttLib.ConnectionParams connectionParams)
                throws MqttException {
        try {
            if (connectionParams.getKey() == null) {
                // TODO: check is unsecured mode supported by SDK/CRT for MQTT 3.1.1 client
                logger.atInfo().log("Creating MqttClientConnection without TLS");
                return AwsIotMqttConnectionBuilder.newDefaultBuilder();
            } else {
                logger.atInfo().log("Creating AwsIotMqttConnectionBuilder with TLS");
                return AwsIotMqttConnectionBuilder.newMtlsBuilder(connectionParams.getCert(), connectionParams.getKey())
                                                    .withCertificateAuthority(connectionParams.getCa());
            }
        } catch (UnsupportedEncodingException ex) {
            logger.atWarn().withThrowable(ex).log("Couldn't create MQTT connection builder");
            throw new MqttException("Couldn't create MQTT connection builder", ex);
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

    private static ConnectResult buildConnectResult(boolean success, Boolean sessionPresent) {
        ConnAckInfo connAckInfo = new ConnAckInfo(sessionPresent);
        return new ConnectResult(success, connAckInfo, null);
    }
}
