/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.addon;

import com.aws.greengrass.testing.mqtt.client.control.api.addon.Event;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventFilter;
import lombok.NonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Implementation storage of the events.
 */
public class EventStorageImpl {

    private final List<Event> events;

    /**
     * Creates instance of EventStorageImpl.
     */
    public EventStorageImpl() {
        this(new LinkedList<>());
    }

    /**
     * Creates instance of EventStorageImpl for tests.
     *
     * @param events the list of events
     */
    EventStorageImpl(List<Event> events) {
        super();
        this.events = events;
    }

    /**
     * Removes all events from storage.
     */
    public void clear() {
        synchronized (events) {
            events.clear();
        }
    }

    /**
     * Adds event to storage.
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull Event event) {
         synchronized (events) {
            events.add(event);
            events.notifyAll();
        }
    }

    /**
     * Gets matched events from storage immediately.
     *
     * @param filter the filter of events
     * @return the list of matched events
     */
    public List<Event> getEvents(@NonNull EventFilter filter) {
        List<Event> result;

        synchronized (events) {
            result = getEventsAsList(filter);
        }

        return result;
    }

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
    public List<Event> awaitEvents(@NonNull EventFilter filter, long timeout, @NonNull TimeUnit unit)
                        throws TimeoutException, InterruptedException {
        List<Event> result;
        final long deadline = unit.toNanos(timeout) + System.nanoTime();

        synchronized (events) {
            result = getEventsAsList(filter);
            while (result.isEmpty()) {
                final long nanos = deadline - System.nanoTime();
                if (nanos <= 0) {
                    throw new TimeoutException("Event doesn't received, timedout");
                }

                TimeUnit.NANOSECONDS.timedWait(events, nanos);
                result = getEventsAsList(filter);
            }
        }
        return result;
    }

    private List<Event> getEventsAsList(@NonNull EventFilter filter) {
        return events.stream().filter(event -> event.isMatched(filter)).collect(Collectors.toList());
    }
}
