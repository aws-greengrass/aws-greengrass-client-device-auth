/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Interface of MQTT5 connection.
 */
public interface MqttConnection {
    int DEFAULT_DISCONNECT_REASON = 4;
    long DEFAULT_DISCONNECT_TIMEOUT = 10;

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
     * Useful information from SUBACK and UNSUBACK packets.
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
     * Closes MQTT connection.
     *
     * @param reasonCode reason why connection is closed
     * @param timeout disconnect operation timeout in seconds
     * @throws MqttException on errors
     */
    void disconnect(int reasonCode, long timeout) throws MqttException;


    /**
     * Publishes MQTT message.
     *
     * @param retain if set message will retained
     * @param qos QoS value to publish message
     * @param timeout publish operation timeout in seconds
     * @param topic topic to publish message
     * @param content message content
     * @return useful information from PUBACK packet or null of no PUBACK has been received (as for QoS 0)
     * @throws MqttException on errors
     */
    PubAckInfo publish(boolean retain, int qos, long timeout, String topic, byte[] content) throws MqttException;

    /**
     * Subscribes to topics.
     *
     * @param timeout subscribe operation timeout in seconds
     * @param subscriptionId optional subscription identificator
     * @param subscriptions list of subscriptions
     * @return useful information from SUBACK packet
     * @throws MqttException on errors
     */
    SubAckInfo subscribe(long timeout, Integer subscriptionId, List<Subscription> subscriptions) throws MqttException;

    /**
     * Unsubscribes from topics.
     *
     * @param timeout subscribe operation timeout in seconds
     * @param filters list of topic filter to unsubscribe
     * @return useful information from UNSUBACK packet
     * @throws MqttException on errors
     */
    SubAckInfo unsubscribe(long timeout, List<String> filters) throws MqttException;
}
