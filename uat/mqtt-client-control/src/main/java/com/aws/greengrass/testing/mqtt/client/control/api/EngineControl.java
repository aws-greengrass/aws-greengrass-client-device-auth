/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.api;

import lombok.NonNull;

import java.io.IOException;

/**
 * Interface to whole engine.
 */
public interface EngineControl {
    int DEFAULT_TIMEOUT = 10;

    /**
     * Interface of receiver for engine level events.
     */
    interface EngineEvents {
        /**
         * Called when new agent is connected to the engine, has been discovered and registered.
         * @param agent new attached agent
         */
        void onAgentAttached(AgentControl agent);

        /**
         * Called just after agent has been unregistered.
         * @param agent detached agent
         */
        void onAgentDeattached(AgentControl agent);
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
     * Starts engine instance.
     *
     * @param port port number to listen for gRPC service, can be 0 to autoselect port
     * @param engineEvents received of engine level events
     * @throws IOException on IO errors
     */
    void startEngine(int port, @NonNull EngineEvents engineEvents) throws IOException;

    /**
     * Gets port where gRPC service actually bound to.
     * @return actual port where gRPC server is listen or null if not running
     */
    Integer getBoundPort();

    /**
     * Get array of local IP addresses.
     *
     * @return the array of IP addresses where control can be reached
     */
    String[] getIPs();

    /**
     * Checks is engine runing.
     *
     * @return true if engine is running
     */
    boolean isEngineRunning();

    /**
     * Gets Agent by agentId.
     *
     * @param agentId id of agent
     * @return AgentControl on success and null if agent does not found
     */
    AgentControl getAgent(@NonNull String agentId);

    /**
     * Waiting until engine will terminated.
     * @throws InterruptedException when thread is interrupted
     */
    void awaitTermination() throws InterruptedException;

    /**
     * Gets connection control by connection name.
     * Searching over all agents and find first occurrence of control with such name
     *
     * @param connectionName the logical name of a connection control
     * @return connection control with that name or null if control does not found
     */
    ConnectionControl getConnectionControl(@NonNull String connectionName);

    /**
     * Stops engine instance.
     *
     */
    void stopEngine();
}
