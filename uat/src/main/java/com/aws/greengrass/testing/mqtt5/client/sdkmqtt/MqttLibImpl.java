/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.sdkmqtt;

import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interface of MQTT5 library.
 */
public class MqttLibImpl implements MqttLib {
    private static final Logger logger = LogManager.getLogger(MqttLibImpl.class);

    private final ConcurrentHashMap<Integer, MqttConnection> connections = new ConcurrentHashMap<>();
    private final AtomicInteger nextConnectionId = new AtomicInteger();

    /**
     * Creates a MQTT5 connection.
     *
     * @param connectionParams connection parameters
     * @return MqttConnection on success
     * @throws MqttException on errors
     */
    @Override
    public MqttConnection createConnection(ConnectionParams connectionParams) throws MqttException {
        return new MqttConnectionImpl(connectionParams);
    }

    /**
     * Register the MQTT connection.
     *
     * @param mqttConnection connection to register
     * @return id of connection
     */
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

    /**
     * Get MQTT connection and remove from list.
     *
     * @param connectionId id of connection
     * @return MqttConnection on success and null when connection does not found
     */
    @Override
    public MqttConnection unregisterConnection(int connectionId) {
        return connections.remove(connectionId);
    }

    /**
     * Get a MQTT connection.
     *
     * @param connectionId id of connection
     * @return MqttConnection on success and null when connection does not found
     */
    @Override
    public MqttConnection getConnection(int connectionId) {
        return connections.get(connectionId);
    }


    @Override
    public void close() {
        cleaupConnections();
    }


    private void cleaupConnections() {
        connections.forEach((key, connection) -> {
            try {
                // delete if value is up to date, otherwise leave for next round
                if (connections.remove(key, connection)) {
                    connection.disconnect(MqttConnection.DEFAULT_DISCONNECT_REASON);
                }
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("failed during disconnect");
            }
        });
    }
}
