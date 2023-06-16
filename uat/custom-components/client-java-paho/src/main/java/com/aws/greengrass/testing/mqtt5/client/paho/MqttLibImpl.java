/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.paho;

import com.aws.greengrass.testing.mqtt311.client.paho.Mqtt311ConnectionImpl;
import com.aws.greengrass.testing.mqtt5.client.GRPCClient;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import lombok.NonNull;
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

    /** Factory of connections. */
    private final ConnectionFactory connectionFactory;


    interface ConnectionFactory {
        MqttConnection newConnection(@NonNull ConnectionParams connectionParams, @NonNull GRPCClient grpcClient)
                throws MqttException, org.eclipse.paho.client.mqttv3.MqttException,
                org.eclipse.paho.mqttv5.common.MqttException;
    }

    /**
     * Creates instance of MqttLibImpl.
     */
    public MqttLibImpl() {
        this(new ConnectionFactory() {
            @Override
            public MqttConnection newConnection(@NonNull ConnectionParams connectionParams,
                                                @NonNull GRPCClient grpcClient)
                    throws org.eclipse.paho.client.mqttv3.MqttException, org.eclipse.paho.mqttv5.common.MqttException {
                if (connectionParams.isMqtt50()) {
                    return new MqttConnectionImpl(connectionParams, grpcClient);
                } else {
                    return new Mqtt311ConnectionImpl(connectionParams, grpcClient);
                }
            }
        });
    }

    /**
     * Creates instance of MqttLibImpl for tests.
     *
     * @param connectionFactory the factory of connections
     */
    MqttLibImpl(@NonNull ConnectionFactory connectionFactory) {
        super();
        this.connectionFactory = connectionFactory;
    }

    @Override
    public MqttConnection createConnection(@NonNull ConnectionParams connectionParams, @NonNull GRPCClient grpcClient)
            throws MqttException {
        try {
            return connectionFactory.newConnection(connectionParams, grpcClient);
        } catch (org.eclipse.paho.client.mqttv3.MqttException | org.eclipse.paho.mqttv5.common.MqttException e) {
            throw new MqttException(e);
        }
    }

    @Override
    public int registerConnection(@NonNull MqttConnection mqttConnection) {
        int connectionId = 0;
        final boolean[] wasSet = new boolean[1];
        while (!wasSet[0]) {
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
                            MqttConnection.DEFAULT_DISCONNECT_REASON, null);
                }
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("Failed during disconnect");
            }
        });
    }
}
