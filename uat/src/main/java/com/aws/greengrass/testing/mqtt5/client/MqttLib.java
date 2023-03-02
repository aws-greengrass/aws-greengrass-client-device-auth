/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import lombok.Builder;
import lombok.Data;

/**
 * Interface of MQTT5 library.
 */
public interface MqttLib extends AutoCloseable {

    @Data
    @Builder
    class ConnectionParams {
        private String clientId;
        private String host;
        private int port;
        private int keepalive;
        private boolean cleanSession;
        private int connectTimeout;
        private String ca;                      // optional
        private String cert;                    // optional
        private String key;                     // optional
    }

    /**
     * Creates a MQTT connection.
     *
     * @param connectionParams connection parameters
     * @return MqttConnection on success
     * @throws MqttException on errors
     */
    MqttConnection createConnection(ConnectionParams connectionParams) throws MqttException;

    /**
     * Register the MQTT connection.
     *
     * @param mqttConnection connection to register
     * @return id of connection
     */
    int registerConnection(MqttConnection mqttConnection);

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
