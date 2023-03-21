/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Interface of MQTT5 connection.
 */
public interface MqttConnection {
    int DEFAULT_DISCONNECT_REASON = 4;
    long DEFAULT_DISCONNECT_TIMEOUT = 10;


    /**
     * Useful information from CONNACK packet.
     */
    @Getter
    @AllArgsConstructor
    class ConnAckInfo {
        private Boolean sessionPresent;
        private Integer reasonCode;
        private Integer sessionExpiryInterval;
        private Integer receiveMaximum;
        private Integer maximumQoS;
        private Boolean retainAvailable;
        private Integer maximumPacketSize;
        private String assignedClientId;
        private String reasonString;
        private Boolean wildcardSubscriptionsAvailable;
        private Boolean subscriptionIdentifiersAvailable;
        private Boolean sharedSubscriptionsAvailable;
        private Integer serverKeepAlive;
        private String responseInformation;
        private String serverReference;

        // TODO: int topicAliasMaximum;          // miss for AWS IoT device SDK MQTT5 client ?
        // TODO: Authentication Method
        // TODO: Authentication Data
        // TODO: user's Properties
    }

    /**
     * Information about start (connect) result.
     */
    @Getter
    @AllArgsConstructor
    class ConnectResult {
        /** Useful information from CONNACK packet. Can missed. */
        private boolean connected;
        private ConnAckInfo connAckInfo;
        private String error;
    }

    /**
     * Information about single subscription.
     */
    @Getter
    @AllArgsConstructor
    class Subscription {
        /** Topic filter. */
        private String filter;

        /** Maximum QoS. */
        private int qos;

        /** No local subscription. */
        private boolean noLocal;

        private boolean retainAsPublished;
        private int retainHandling;
    }

    /**
     * Useful information from SUBACK packet.
     */
    @Getter
    @AllArgsConstructor
    class SubAckInfo {
        /** Reason codes. */
        private List<Integer> reasonCodes;

        private String reasonString;
        // TODO: add user's properties
    }

    /**
     * Contains information about publishing MQTT v5.0 message.
     */
    @Getter
    @Builder
    class Message {
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
     * Useful information from PUBACK packet.
     */
    @Getter
    @AllArgsConstructor
    class PubAckInfo {
        /** MQTT v5.0 Reason code of PUBACK packet. */
        private Integer reasonCode;

        /** MQTT v5.0 Reason string of PUBACK packet. */
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
     * @return ConnectResult on success
     * @throws MqttException on errors
     */
    ConnectResult start(long timeout, int connectionId) throws MqttException;

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