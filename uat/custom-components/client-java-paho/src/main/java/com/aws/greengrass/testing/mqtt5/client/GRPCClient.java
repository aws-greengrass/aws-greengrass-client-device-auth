/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Interface to gRPC client.
 */
public interface GRPCClient {
    /**
     * Contains information about received MQTT v5.0 message.
     */
    @Getter
    @AllArgsConstructor
    class MqttReceivedMessage {
        /** QoS value. */
        private int qos;

        /** Retain flag. */
        private boolean retain;

        /** Topic of message. */
        private String topic;

        /** Payload of message. */
        private byte[] payload;

        // TODO: add user's properties and so one
    }


    /**
     * Contains information from DISCONNECT packet.
     */
    @Getter
    @AllArgsConstructor
    class DisconnectInfo {
        /** Disconnect reason code. */
        Integer reasonCode;

        /** Sessions expiry interval. */
        Integer sessionExpiryInterval;

        /** Disconnect reason string. */
        String reasonString;

        /** Server reference. */
        String serverReference;

        // TODO: add user's properties
    }

    /**
     * Called when MQTT message is receive my MQTT client and deliver information from it to gRPC server.
     *
     * @param connectionId connection id which receives the message
     * @param message information from the received MQTT message
     */
    void onReceiveMqttMessage(int connectionId, MqttReceivedMessage message);

    /**
     * Called when MQTT connection has been disconnected by client or server side.
     *
     * @param connectionId connection id which receives the message
     * @param disconnectInfo optional infomation from DISCONNECT packet
     * @param error optional OS-level error string
     */
    void onMqttDisconnect(int connectionId, DisconnectInfo disconnectInfo, String error);
}
