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
import software.amazon.awssdk.iot.AwsIotMqttConnectionBuilder;

import java.io.UnsupportedEncodingException;
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
        } catch (TimeoutException | InterruptedException | ExecutionException ex) {
            logger.atError().withThrowable(ex).log("Exception occurred during connect");
            throw new MqttException("Exception occurred during connect", ex);
        }
    }

    @Override
    public void disconnect(long timeout, int reasonCode) throws MqttException {

        if (!isClosing.getAndSet(true)) {
            CompletableFuture<Void> disconnected = connection.disconnect();
            try {
                disconnected.get(timeout, TimeUnit.SECONDS);
            logger.atInfo().log("MQTT 3.1.1 connection {} has been closed", connectionId);
            } catch (TimeoutException | InterruptedException | ExecutionException ex) {
                logger.atError().withThrowable(ex).log("Failed during disconnecting");
                throw new MqttException("Could not disconnect", ex);
            } finally {
                connection.close();
            }
        }
    }

    @Override
    public PubAckInfo publish(long timeout, final @NonNull Message message)
                    throws MqttException {

        stateCheck();

        return null;
    }

    @Override
    public SubAckInfo subscribe(long timeout, Integer subscriptionId, final @NonNull List<Subscription> subscriptions)
            throws MqttException {

        stateCheck();

        return null;
    }

    @Override
    public UnsubAckInfo unsubscribe(long timeout, final @NonNull List<String> filters)
            throws MqttException {

        stateCheck();

        return null;
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
