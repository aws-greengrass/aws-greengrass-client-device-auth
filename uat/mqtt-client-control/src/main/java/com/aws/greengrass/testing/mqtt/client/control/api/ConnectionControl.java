/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.api;

import com.aws.greengrass.testing.mqtt.client.Mqtt5ConnAck;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Subscription;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;

/**
 * Control of single MQTT connection.
 */
public interface ConnectionControl {
    /**
     * Gets id of the connection.
     * @return agent id
     */
    int getConnectionId();

    /**
     * Gets information from CONNACK packet.
     * @return CONNACK response from broker to CONNECT packet
     */
    Mqtt5ConnAck getConnAck();

    /**
     * Gets value of timeout.
     */
    int getTimeout();

    /**
     * Sets value of timeout.
     * By default timeout value is getting from agent object
     *
     * @param timeout value of timeout in seconds
     */
    void setTimeout(int timeout);

    /**
     * Do MQTT subscription(s).
     *
     * @param subscriptionId optional subscription id
     * @param subscriptions MQTT v5.0 subscriptions
     * @return reply to subscribe
     * @throws StatusRuntimeException on errors
     */
    MqttSubscribeReply subscribeMqtt(Integer subscriptionId, Mqtt5Subscription... subscriptions);

    /**
     * Publish MQTT message.
     *
     * @param message message to publish
     * @return publish's response
     * @throws StatusRuntimeException on errors
     */
    MqttPublishReply publishMqtt(Mqtt5Message message);


    /**
     * Remove MQTT subscription(s).
     *
     * @param filters topic filters to unsubscribe
     * @return reply to unsubscribe
     * @throws StatusRuntimeException on errors
     */
    MqttSubscribeReply unsubscribeMqtt(String... filters);

    /**
     * Close MQTT connection to the broker.
     *
     * @param reason reason code of disconnection as specified by MQTT v5.0 in DISCONNECT packet
     * @throws StatusRuntimeException on errors
     */
    void closeMqttConnection(int reason);
}
