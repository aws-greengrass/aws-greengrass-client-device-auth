/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


@ExtendWith({MockitoExtension.class, GGExtension.class})
public class DomainEventsTest {
    private DomainEvents domainEvents;

    static class TestEvent implements DomainEvent {
    }

    static class TestEvent2 implements DomainEvent {
    }

    @BeforeEach
    void beforeEach() {
        domainEvents = new DomainEvents();
    }

    @Test
    void GIVEN_noListener_WHEN_eventEmitted_THEN_itsFine() {
        domainEvents.emit(new TestEvent());
        domainEvents.emit(new TestEvent2());
    }

    @Test
    void GIVEN_twoListenersForSameEvent_WHEN_eventEmitted_THEN_bothListenersFire() {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);

        domainEvents.registerListener((e) -> listener1Count.getAndIncrement(), TestEvent.class);
        domainEvents.registerListener((e) -> listener2Count.getAndIncrement(), TestEvent.class);

        domainEvents.emit(new TestEvent());

        assertThat(listener1Count.get(), is(1));
        assertThat(listener2Count.get(), is(1));
    }

    @Test
    void GIVEN_twoListenersForDifferentEvents_WHEN_eventEmitted_THEN_singleListenerFires() {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);

        domainEvents.registerListener((e) -> listener1Count.getAndIncrement(), TestEvent.class);
        domainEvents.registerListener((e) -> listener2Count.getAndIncrement(), TestEvent2.class);

        domainEvents.emit(new TestEvent());

        assertThat(listener1Count.get(), is(1));
        assertThat(listener2Count.get(), is(0));

        domainEvents.emit(new TestEvent2());

        assertThat(listener1Count.get(), is(1));
        assertThat(listener2Count.get(), is(1));
    }
}
