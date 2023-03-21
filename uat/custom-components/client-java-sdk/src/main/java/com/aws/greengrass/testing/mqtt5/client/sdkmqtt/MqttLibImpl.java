/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.GRPCClient;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of MQTT5 library.
 */
public class MqttLibImpl implements MqttLib {
    private static final Logger logger = LogManager.getLogger(MqttLibImpl.class);

    /** Map of connections by id. */
    private final ConcurrentHashMap<Integer, MqttConnection> connections = new ConcurrentHashMap<>();

    /** Next connection id to use. */
    private final AtomicInteger nextConnectionId = new AtomicInteger();

    @Override
    public MqttConnection createConnection(ConnectionParams connectionParams, GRPCClient grpcClient)
                throws MqttException {
        return new MqttConnectionImpl(connectionParams, grpcClient);
    }

    @Override
    public int registerConnection(MqttConnection mqttConnection) {
        int connectionId = 0;
        final boolean[] wasSet = new boolean[1];
        while (! wasSet[0]) {
            connectionId = nextConnectionId.incrementAndGet();
            connections.computeIfAbsent(connectionId, key -> {
                wasSet[0] = true;
                return mqttConnection;
                });
        }
        return connectionId;
    }

    @Override
    public MqttConnection unregisterConnection(int connectionId) {
        return connections.remove(connectionId);
    }

    @Override
    public MqttConnection getConnection(int connectionId) {
        return connections.get(connectionId);
    }


    @Override
    public void close() {
        cleaupConnections();
    }


    /**
     * Dry connections.
     */
    private void cleaupConnections() {
        connections.forEach((key, connection) -> {
            try {
                // delete if value is up to date, otherwise leave for next round
                if (connections.remove(key, connection)) {
                    connection.disconnect(MqttConnection.DEFAULT_DISCONNECT_TIMEOUT,
                                            MqttConnection.DEFAULT_DISCONNECT_REASON);
                }
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("Failed during disconnect");
            }
        });
    }
}
