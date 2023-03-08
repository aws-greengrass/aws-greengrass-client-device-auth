/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Interface of MQTT5 connection.
 */
public interface MqttConnection {
    int DEFAULT_DISCONNECT_REASON = 4;
    long DEFAULT_DISCONNECT_TIMEOUT = 10;

    /**
     * Contains information about publishing MQTT v5.0 message.
     */
    @Data
    @Builder
    class Message {
        /** QoS value. */
        int qos;

        /** Retain flag. */
        boolean retain;

        /** Topic of message. */
        String topic;

        /** Payload of message. */
        byte[] payload;

        // TODO: add user's properties and so one
    }

    /**
     * Useful information from PUBACK packet.
     */
    @Data
    @AllArgsConstructor
    class PubAckInfo {
        /** MQTT v5.0 Reason code of PUBACK packet. */
        private int reasonCode;

        /** MQTT v5.0 Reason string of PUBACK packet. */
        private String reasonString;
        // TODO: add user's properties
    }


    /**
     * Information about single subscription.
     */
    @Data
    @AllArgsConstructor
    class Subscription {
        /** Topic filter. */
        String filter;

        /** Maximum QoS. */
        int qos;

        /** No local subscription. */
        boolean noLocal;

        boolean retainAsPublished;
        int retainHandling;
    }

    /**
     * Useful information from SUBACK packet.
     */
    @Data
    @AllArgsConstructor
    class SubAckInfo {
        /** Reason codes. */
        private List<Integer> reasonCodes;

        private String reasonString;
        // TODO: add user's properties
    }


    /**
     * Useful information from UNSUBACK packet.
     * Actually is the same as SubAckInfo.
     */
    class UnsubAckInfo extends SubAckInfo {
        public UnsubAckInfo(List<Integer> reasonCodes, String reasonString) {
            super(reasonCodes, reasonString);
        }
    }

    /**
     * Starts MQTT connection.
     *
     * @param timeout connect operation timeout in seconds
     * @param connectionId connection id as assigned by MQTT library
     * @throws MqttException on errors
     */
    void start(long timeout, int connectionId) throws MqttException;

    /**
     * Subscribes to topics.
     *
     * @param timeout subscribe operation timeout in seconds
     * @param subscriptionId optional subscription identificator
     * @param subscriptions list of subscriptions
     * @return useful information from SUBACK packet
     * @throws MqttException on errors
     */
    SubAckInfo subscribe(long timeout, final Integer subscriptionId, final List<Subscription> subscriptions)
            throws MqttException;


    /**
     * Publishes MQTT message.
     *
     * @param timeout publish operation timeout in seconds
     * @param message message to publish
     * @return useful information from PUBACK packet or null of no PUBACK has been received (as for QoS 0)
     * @throws MqttException on errors
     */
    PubAckInfo publish(long timeout, final Message message) throws MqttException;

    /**
     * Unsubscribes from topics.
     *
     * @param timeout subscribe operation timeout in seconds
     * @param filters list of topic filter to unsubscribe
     * @return useful information from UNSUBACK packet
     * @throws MqttException on errors
     */
    UnsubAckInfo unsubscribe(long timeout, final List<String> filters) throws MqttException;

    /**
     * Closes MQTT connection.
     *
     * @param timeout disconnect operation timeout in seconds
     * @param reasonCode reason why connection is closed
     * @throws MqttException on errors
     */
    void disconnect(long timeout, int reasonCode) throws MqttException;
}
