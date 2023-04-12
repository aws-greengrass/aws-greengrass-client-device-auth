/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.api.addon;

import lombok.NonNull;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Interface to storage of the events.
 */
public interface EventStorage {

    /**
     * Removes all events from storage.
     */
    void clear();

    /**
     * Adds event to storage.
     *
     * @param event the event to add
     */
    void addEvent(@NonNull Event event);

    /**
     * Gets matched events from storage immediately.
     *
     * @param filter the filter of events
     * @return the list of matched events
     */
    List<Event> getEvents(@NonNull EventFilter filter);

    /**
     * Waiting for matched events.
     * Will return immediately when matched event already exists.
     * Else will return as soon as at least one matched event added.
     *
     * @param filter the filter of events
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the list of matched events
     * @throws TimeoutException if the wait timed out
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    List<Event> awaitEvents(@NonNull EventFilter filter, long timeout, @NonNull TimeUnit unit)
                throws TimeoutException, InterruptedException;
}
