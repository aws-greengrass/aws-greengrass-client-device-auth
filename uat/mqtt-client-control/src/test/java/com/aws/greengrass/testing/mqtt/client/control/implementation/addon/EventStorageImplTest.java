/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.addon;

import com.aws.greengrass.testing.mqtt.client.control.api.addon.Event;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class EventStorageImplTest {
    List<Event> actualResultEvents = null;

    List<Event> events;

    EventStorageImpl eventStorageImpl;

    @BeforeEach
    void setup() {
        events = new LinkedList<>();
        eventStorageImpl = new EventStorageImpl(events);
    }

    @Test
    void GIVEN_event_WHEN_add_event_THEN_event_added() {
        // GIVEN
        assertEquals(0, events.size());
        Event event = mock(Event.class);

        // WHEN
        eventStorageImpl.addEvent(event);

        // THEN
        assertEquals(1, events.size());
    }


    @Test
    void GIVEN_non_empty_event_storage_WHEN_clear_THEN_storage_empty() {
        // GIVEN
        Event event = mock(Event.class);
        eventStorageImpl.addEvent(event);
        assertEquals(1, events.size());

        // WHEN
        eventStorageImpl.clear();

        // THEN
        assertEquals(0, events.size());
    }

    @Test
    void GIVEN_have_events_and_matched_WHEN_get_events_THEN_all_events_returned() {
        // GIVEN
        final Event event = mock(Event.class);
        final EventFilter eventFilter = mock(EventFilter.class);
        lenient().when(event.isMatched(any(EventFilter.class))).thenReturn(true);
        events.add(event);

        // WHEN
        final List<Event> resultEvents = eventStorageImpl.getEvents(eventFilter);

        // THEN
        assertEquals(events, resultEvents);
    }

    @Test
    void GIVEN_have_events_no_one_matched_WHEN_get_events_THEN_zero_events_returned() {
        // GIVEN
        final Event event = mock(Event.class);
        final EventFilter eventFilter = mock(EventFilter.class);
        lenient().when(event.isMatched(any(EventFilter.class))).thenReturn(false);
        events.add(event);

        // WHEN
        final List<Event> resultEvents = eventStorageImpl.getEvents(eventFilter);

        // THEN
        assertEquals(0, resultEvents.size());
    }

    @Test
    void GIVEN_have_events_and_matched_WHEN_await_events_THEN_all_events_returned() throws TimeoutException, InterruptedException {
        // GIVEN
        final Event event = mock(Event.class);
        final EventFilter eventFilter = mock(EventFilter.class);
        lenient().when(event.isMatched(any(EventFilter.class))).thenReturn(true);
        eventStorageImpl.addEvent(event);

        // WHEN
        final List<Event> resultEvents = eventStorageImpl.awaitEvents(eventFilter, 1, TimeUnit.SECONDS);

        // THEN
        assertEquals(events, resultEvents);
    }

    @Test
    void GIVEN_have_events_no_one_matched_WHEN_await_events_THEN_timedout() throws TimeoutException, InterruptedException {
        // GIVEN
        final Event event = mock(Event.class);
        final EventFilter eventFilter = mock(EventFilter.class);
        lenient().when(event.isMatched(any(EventFilter.class))).thenReturn(false);
        eventStorageImpl.addEvent(event);

        // WHEN
        TimeoutException thrown = assertThrows(TimeoutException.class, () -> {
            eventStorageImpl.awaitEvents(eventFilter, 1, TimeUnit.SECONDS);
        });
        assertEquals("Event doesn't received, timedout", thrown.getMessage());
    }


    @Test
    void GIVEN_await_events_WHEN_add_matched_event_THEN_all_events_returned() throws TimeoutException, InterruptedException {
        // GIVEN
        final Event event = mock(Event.class);
        final EventFilter eventFilter = mock(EventFilter.class);
        lenient().when(event.isMatched(any(EventFilter.class))).thenReturn(true);

        // WHEN
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {
                // 2 seconds sleep
                Thread.sleep(2000);
                eventStorageImpl.addEvent(event);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        // 2.1 seconds timeout, 100 ms gap
        assertTimeout(Duration.ofMillis(2100), () -> {
            // 5 seconds waiting
            actualResultEvents = eventStorageImpl.awaitEvents(eventFilter, 5, TimeUnit.SECONDS);
        });

        // THEN
        assertNotNull(actualResultEvents);
        assertEquals(1, actualResultEvents.size());
        assertEquals(events, actualResultEvents);
    }
}
