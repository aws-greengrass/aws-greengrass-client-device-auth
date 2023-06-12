/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.api;

import com.aws.greengrass.testing.mqtt.client.Mqtt5ConnAck;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Properties;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Subscription;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import lombok.NonNull;

import java.util.List;

/**
 * Control of single MQTT connection.
 */
public interface ConnectionControl {

    /**
     * Gets agent control for this connection control.
     *
     * @return the agent control
     */
    AgentControl getAgentControl();

    /**
     * Gets id of the connection.
     *
     * @return connection id
     */
    int getConnectionId();

    /**
     * Sets connection name.
     * Can be used instead of agentId:connectionId pair to identify connection
     *
     * @param connectionName logical connection name
     */
    void setConnectionName(@NonNull String connectionName);

    /**
     * Gets connection name.
     *
     * @return logical connection name
     */
    String getConnectionName();

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
     * @param mqtt5Properties MQTT v5.0 properties
     * @return reply to subscribe
     * @throws StatusRuntimeException on errors
     */
    MqttSubscribeReply subscribeMqtt(Integer subscriptionId, List<Mqtt5Properties> mqtt5Properties,
                                     @NonNull Mqtt5Subscription... subscriptions);

    /**
     * Publish MQTT message.
     *
     * @param message message to publish
     * @return publish's response
     * @throws StatusRuntimeException on errors
     */
    MqttPublishReply publishMqtt(@NonNull Mqtt5Message message);


    /**
     * Remove MQTT subscription(s).
     *
     * @param filters topic filters to unsubscribe
     * @return reply to unsubscribe
     * @throws StatusRuntimeException on errors
     */
    MqttSubscribeReply unsubscribeMqtt(@NonNull String... filters);

    /**
     * Close MQTT connection to the broker.
     *
     * @param reason reason code of disconnection as specified by MQTT v5.0 in DISCONNECT packet
     * @throws StatusRuntimeException on errors
     */
    void closeMqttConnection(int reason);
}
