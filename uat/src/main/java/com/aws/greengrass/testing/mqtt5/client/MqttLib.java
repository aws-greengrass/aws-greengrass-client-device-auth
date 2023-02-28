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
public interface MqttLib {

    @Data
    @Builder
    class ConnectRequest {
        private String clientId;
        private String host;
        private int port;
        private int keepalive;
        private boolean cleanSession;
        private String ca;
        private String cert;
        private String key;
    }

    /**
     * Creates a MQTT5 connection.
     *
     * @param connectionRequest connect arguments
     * @return MqttConnection on success
     * @throws MqttException on errors
     */
    MqttConnection createConnection(ConnectRequest connectionRequest) throws MqttException;
}
