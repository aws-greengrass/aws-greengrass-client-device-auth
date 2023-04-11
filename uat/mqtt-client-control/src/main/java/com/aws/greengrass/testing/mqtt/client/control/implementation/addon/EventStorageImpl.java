/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.addon;

import com.aws.greengrass.testing.mqtt.client.control.api.addon.Event;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventFilter;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventStorage;
import lombok.NonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Implementation storage of the events.
 */
public class EventStorageImpl implements EventStorage {

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

    @Override
    public void clear() {
        synchronized (events) {
            events.clear();
        }
    }

    @Override
    public void addEvent(@NonNull Event event) {
         synchronized (events) {
            events.add(event);
            events.notifyAll();
        }
    }

    @Override
    public List<Event> getEvents(@NonNull EventFilter filter) {
        List<Event> result;

        synchronized (events) {
            result = getEventsAsList(filter);
        }

        return result;
    }

    @Override
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
