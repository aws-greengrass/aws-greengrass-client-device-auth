/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.addon.implementation;

import com.aws.greengrass.testing.mqtt.client.control.addon.api.Event;
import com.aws.greengrass.testing.mqtt.client.control.addon.api.EventFilter;
import com.aws.greengrass.testing.mqtt.client.control.addon.api.EventStorage;
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

    private final List<Event> events = new LinkedList<>();

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
    public List<Event> awatingEvents(@NonNull EventFilter filter, long timeout, @NonNull TimeUnit unit)
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
