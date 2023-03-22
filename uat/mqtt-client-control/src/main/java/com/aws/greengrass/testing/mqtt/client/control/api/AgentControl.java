/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.api;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.MqttConnectRequest;

/**
 * Control of single MQTT client (agent).
 */
public interface AgentControl {
    int DEFAULT_TIMEOUT = 10;

    /**
     * Interface of receiver for connection level events.
     */
    interface ConnectionEvents {
        /**
         * Called when MQTT client received MQTT message.
         *
         * @param connectionControl connection which received the message
         * @param message received message
         */
        void onMessageReceived(ConnectionControl connectionControl, Mqtt5Message message);

        /**
         * Called when MQTT connection has been disconnected.
         *
         * @param connectionControl connection which received the message
         * @param disconnect optional infomation from DISCONNECT packet
         * @param error optional OS-dependent error string
         */
        void onMqttDisconnect(ConnectionControl connectionControl, Mqtt5Disconnect disconnect, String error);
    }

    /**
     * Gets value of timeout.
     */
    int getTimeout();

    /**
     * Sets value of timeout.
     * By default timeout value is DEFAULT_TIMEOUT;
     *
     * @param timeout value of timeout in seconds
     */
    void setTimeout(int timeout);

    /**
     * Starts agent.
     */
    void startAgent();

    /**
     * Stops agent.
     */
    void stopAgent();

    /**
     * Gets id of the agent.
     * @return agent id
     */
    String getAgentId();

    /**
     * Create MQTT connection to a broker.
     *
     * @param connectRequest parameters of MQTT connect
     * @param connectionEvents handler of connection events
     * @return new connection instance
     * @throws RuntimeException on errors
     */
    ConnectionControl createMqttConnection(MqttConnectRequest connectRequest, ConnectionEvents connectionEvents);

    /**
     * Shutdown whole agent.
     *
     * @param reason shutdown reason
     * @throws StatusRuntimeException on errors
     */
    void shutdownAgent(String reason);
}
