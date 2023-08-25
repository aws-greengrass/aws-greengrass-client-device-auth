/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.api;

import com.aws.greengrass.testing.mqtt.client.CoreDeviceDiscoveryReply;
import com.aws.greengrass.testing.mqtt.client.CoreDeviceDiscoveryRequest;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.MqttConnectRequest;
import lombok.NonNull;

/**
 * Control of single MQTT client (agent).
 */
public interface AgentControl {
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
     * By default timeout value is taken from EngineControl;
     *
     * @param timeout value of timeout in seconds
     */
    void setTimeout(int timeout);

    /**
     * Starts the agent control.
     */
    void startAgent();

    /**
     * Stops the agent control.
     *
     * @param sendShutdown when set shutdown request will be sent to agent and all MQTT connection are closed
     */
    void stopAgent(boolean sendShutdown);

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
    ConnectionControl createMqttConnection(@NonNull MqttConnectRequest connectRequest,
                                            @NonNull ConnectionEvents connectionEvents);

    /**
     * Gets connection control by connection name.
     * Searching over all controls and find first occurrence of control with such name
     *
     * @param connectionName the logical name of a connection
     * @return connection control or null when does not found
     */
    ConnectionControl getConnectionControl(@NonNull String connectionName);

    /**
     * Shutdown whole agent.
     *
     * @param reason shutdown reason
     * @throws io.grpc.StatusRuntimeException on errors
     */
    void shutdownAgent(String reason);


    /**
     * Do discovery of Core device broker.
     *
     * @param discoveryRequest the request with clients name and credentials
     * @return the reply with connectivity information of IoT Core device broker
     * @throws io.grpc.StatusRuntimeException on errors
     */
    CoreDeviceDiscoveryReply discoveryCoreDevice(@NonNull CoreDeviceDiscoveryRequest discoveryRequest);
}
