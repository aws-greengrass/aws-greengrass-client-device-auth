/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.addon;

import com.aws.greengrass.testing.mqtt.client.control.api.addon.Event;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventFilter;
import lombok.NonNull;


/**
 * Implements abstrast base of the events implementation.
 */
abstract class EventImpl implements Event {

    private final Type type;
    private final long timestamp;

    /**
     * Creates instance of EventImpl.
     *
     * @param type the type of the message
     */
    EventImpl(@NonNull Type type) {
        super();
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean isMatched(@NonNull EventFilter filter) {

        // check event type
        final Event.Type type = filter.getType();
        if (type != null && type != getType()) {
            return false;
        }

        // check timestamp borders
        return compareTimestamps(filter.getFromTimestamp(), filter.getToTimestamp());
    }

    private boolean compareTimestamps(Long from, Long to) {
        // check from timestamp
        if (from != null &&  getTimestamp() < from) {
            return false;
        }

        return to == null || to > getTimestamp();
    }
}
