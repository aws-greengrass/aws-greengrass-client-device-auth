/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.GRPCClient;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.mqttv5.client.IMqttClient;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of MQTT5 connection based on AWS IoT device SDK.
 */
public class MqttConnectionImpl implements MqttConnection {
    private static final Logger logger = LogManager.getLogger(MqttConnectionImpl.class);

    private final AtomicBoolean isClosing = new AtomicBoolean();
    private final AtomicBoolean isConnected = new AtomicBoolean();

    private final GRPCClient grpcClient;
    private final MqttConnectionOptions connectionOptions;
    private final IMqttClient client;
    private int connectionId = 0;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();        // TODO: use DI

    /**
     * Creates a MQTT5 connection.
     *
     * @param connectionParams the connection parameters
     * @param grpcClient the consumer of received messages and disconnect events
     * @throws MqttException on errors
     */
    public MqttConnectionImpl(@NonNull MqttLib.ConnectionParams connectionParams, @NonNull GRPCClient grpcClient) throws org.eclipse.paho.mqttv5.common.MqttException {
        super();

        this.grpcClient = grpcClient;
        this.client = createClient(connectionParams);
        this.connectionOptions = convertParams(connectionParams);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public ConnectResult start(long timeout, int connectionId) throws org.eclipse.paho.mqttv5.common.MqttException, MqttException {
        this.connectionId = connectionId;
        try {
            IMqttToken token = client.connectWithResult(connectionOptions);
            token.waitForCompletion();
            return buildConnectResult(true, token);
        } catch (Exception ex) {
            logger.atError().withThrowable(ex).log("Exception occurred during connect");
            throw new MqttException("Exception occurred during connect", ex);
        }
    }

    @SuppressWarnings({"PMD.UseTryWithResources", "PMD.AvoidCatchingGenericException"})
    @Override
    public void disconnect(long timeout, int reasonCode) throws MqttException, org.eclipse.paho.mqttv5.common.MqttException {
        if (!isClosing.getAndSet(true)) {
            try {
                client.disconnect();
            } catch (Exception ex) {
                logger.atError().withThrowable(ex).log("Failed during disconnecting from MQTT broker");
                throw new MqttException("Could not disconnect", ex);
            } finally {
                client.close();
            }
        }
    }

    /**
     * Creates a MQTT5 client.
     *
     * @param connectionParams connection parameters
     */
    private IMqttClient createClient(MqttLib.ConnectionParams connectionParams) throws org.eclipse.paho.mqttv5.common.MqttException {
        return new MqttClient(connectionParams.getHost(),
                connectionParams.getClientId(), new MemoryPersistence());
    }

    private MqttConnectionOptions convertParams(MqttLib.ConnectionParams connectionParams) {
        MqttConnectionOptions connectionOptions = new MqttConnectionOptions();
        connectionOptions.setServerURIs(new String[] {connectionParams.getHost()});

        return connectionOptions;
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

    private static ConnectResult buildConnectResult(boolean success, IMqttToken token) {
        ConnAckInfo connAckInfo = convertConnAckPacket(token);
        return new ConnectResult(success, connAckInfo, null);
    }

    private static ConnAckInfo convertConnAckPacket(IMqttToken token) {
        if (token == null) {
            return null;
        }

        int[] reasonCodes = token.getReasonCodes();
        return new ConnAckInfo(token.getSessionPresent(),
                (reasonCodes == null || reasonCodes.length == 0) ? null : reasonCodes[0],
                convertLongToInteger(token.getRequestProperties().getSessionExpiryInterval()),
                                token.getResponseProperties().getReceiveMaximum(),
                                token.getResponseProperties().getMaximumQoS(),
                                token.getResponseProperties().isRetainAvailable(),
                convertLongToInteger(token.getResponseProperties().getMaximumPacketSize()),
                                token.getResponseProperties().getAssignedClientIdentifier(),
                                token.getResponseProperties().getReasonString(),
                                token.getRequestProperties().isWildcardSubscriptionsAvailable(),
                                token.getRequestProperties().isSubscriptionIdentifiersAvailable(),
                                token.getRequestProperties().isSharedSubscriptionAvailable(),
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
