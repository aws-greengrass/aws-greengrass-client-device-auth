/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.api.addon;

import lombok.NonNull;

/**
 * Interface to generic event.
 */
public interface Event {
    /** Type of the event. */
    enum Type {
        /** MQTT message is received. */
        EVENT_TYPE_MQTT_MESSAGE,

        /** MQTT connection is disconnected. */
        EVENT_TYPE_MQTT_DISCONNECTED,

        // TODO: implement other events
        // /** MQTT Connecttion established. */
        // EVENT_TYPE_MQTT_CONNECTED,

        // /** Agent is registered and discovered. */
        // EVENT_TYPE_AGENT_DISCOVERED,

        // /** Agent is unregistered. */
        // EVENT_TYPE_AGENT_UNREGISTERED,
    }

    /**
     * Gets type of event.
     *
     * @return type of the event
     */
    Type getType();

    /**
     * Gets name of connection generated the event.
     *
     * @return name of the related connection
     */
    String getConnectionName();


    /**
     * Gets timestamp of the event.
     *
     * @return the value of System.currentTimeMillis() on the moment when event was been created
     */
    long getTimestamp();

    /**
     * Checks is the event matches to filter.
     *
     * @param filter the filter to match event
     * @return true when the event matches to filter
     */
    boolean isMatched(@NonNull EventFilter filter);
}
