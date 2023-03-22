/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.api;

import java.io.IOException;

/**
 * Interface to whole engine.
 */
public interface EngineControl {

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
     * Starts engine instance.
     *
     * @param port port number to listen for gRPC service
     * @param engineEvents received of engine level events
     * @throws IOException on IO errors
     */
    void startEngine(int port, EngineEvents engineEvents) throws IOException;

    /**
     * Waiting until engine will terminated.
     * @throws InterruptedException when thread is interrupted
     */
    void awaitTermination() throws InterruptedException;

    /**
     * Stops engine instance.
     *
     */
    void stopEngine();
}
