/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Properties;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.util.List;

/**
 * Interface of MQTT5 connection.
 */
public interface MqttConnection {
    int DEFAULT_DISCONNECT_REASON = 0;       // MQTT_RC_NORMAL_DISCONNECTION
    long DEFAULT_DISCONNECT_TIMEOUT = 10;
    long MIN_SHUTDOWN_NS = 200_000_000;      // 200ms


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
        private List<Mqtt5Properties>  userProperties;

        // TODO: int topicAliasMaximum;          // miss for AWS IoT device SDK MQTT5 client ?
        // TODO: Authentication Method
        // TODO: Authentication Data

        /**
         * Creates ConnAckInfo for result of MQTT 3.1.1 connect.
         *
         * @param sessionPresent the session present flag of MQTT 3.1.1
         */
        public ConnAckInfo(Boolean sessionPresent) {
            super();
            this.sessionPresent = sessionPresent;
        }
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

        /** Optional user properties. */
        private List<Mqtt5Properties>  userProperties;

        /** Optional content type. */
        private String contentType;

        /** Optional payload format indicator. */
        private Boolean payloadFormatIndicator;

        // TODO: add user's properties and so one
    }

    /**
     * Starts MQTT connection.
     *
     * @param connectionParams connect parameters
     * @param connectionId connection id as assigned by MQTT library
     * @return ConnectResult on success
     * @throws MqttException on errors
     */
    ConnectResult start(MqttLib.ConnectionParams connectionParams, int connectionId) throws MqttException;

    /**
     * Subscribes to topics.
     *
     * @param timeout subscribe operation timeout in seconds
     * @param subscriptions list of subscriptions
     * @param userProperties  list of user's properties MQTT v5.0
     * @return useful information from SUBACK packet
     */
    MqttSubscribeReply subscribe(long timeout, @NonNull List<Subscription> subscriptions,
                                 List<Mqtt5Properties> userProperties);

    /**
     * Closes MQTT connection.
     *
     * @param timeout disconnect operation timeout in seconds
     * @param reasonCode reason why connection is closed
     * @param userProperties  list of user's properties MQTT v5.0
     * @exception MqttException on errors
     */
    void disconnect(long timeout, int reasonCode, List<Mqtt5Properties> userProperties) throws MqttException;

    /**
     * Publishes MQTT message.
     *
     * @param timeout publish operation timeout in seconds
     * @param message message to publish
     * @return useful information from PUBACK packet or null of no PUBACK has been received (as for QoS 0)
     */
    MqttPublishReply publish(long timeout, @NonNull Message message);

    /**
     * Unsubscribes from topics.
     *
     * @param timeout subscribe operation timeout in seconds
     * @param filters list of topic filter to unsubscribe
     * @param userProperties list of user's properties MQTT v5.0
     * @return useful information from UNSUBACK packet
     */
    MqttSubscribeReply unsubscribe(long timeout, @NonNull List<String> filters,
                                   List<Mqtt5Properties> userProperties);

}
