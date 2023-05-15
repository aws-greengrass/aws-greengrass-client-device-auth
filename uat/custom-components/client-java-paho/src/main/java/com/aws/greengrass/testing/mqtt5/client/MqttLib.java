/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

/**
 * Interface of MQTT5 library.
 */
public interface MqttLib extends AutoCloseable {

    /**
     * MQTT connection parameters.
     */
    @Data
    @Builder
    class ConnectionParams {
        /** MQTT client id. */
        private String clientId;

        /** Host name of IP address of MQTT broker. */
        private String host;

        /** Port of MQTT broker. */
        private int port;

        /** Connection keep alive interval in seconds. */
        private int keepalive;

        /** Clean session (clean start) flag of CONNECT packet. */
        private boolean cleanSession;

        /** Content of CA, optional. */
        private String ca;

        /** Content of MQTT client's certificate, optional. */
        private String cert;

        /** Content of MQTT client's private key, optional. */
        private String key;

        /** The true MQTT v5.0 connection is requested. */
        private boolean mqtt50;

        /** The true MQTT v5.0 connection is requested. */
        private int connectionTimeout;
    }

    /**
     * Creates a MQTT connection.
     *
     * @param connectionParams connection parameters
     * @param grpcClient consumer of received messages and disconnect events
     * @return MqttConnection on success
     * @throws MqttException on errors
     */
    MqttConnection createConnection(@NonNull ConnectionParams connectionParams, @NonNull GRPCClient grpcClient)
            throws MqttException;

    /**
     * Register the MQTT connection.
     *
     * @param mqttConnection connection to register
     * @return id of connection
     */
    int registerConnection(@NonNull MqttConnection mqttConnection);

    /**
     * Get a MQTT connection.
     *
     * @param connectionId id of connection
     * @return MqttConnection on success and null when connection does not found
     */
    MqttConnection getConnection(int connectionId);

    /**
     * Get MQTT connection and remove from list.
     *
     * @param connectionId id of connection
     * @return MqttConnection on success and null when connection does not found
     */
    MqttConnection unregisterConnection(int connectionId);
}
