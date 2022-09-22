/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;


import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.events.ThingEvent;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class ThingRegistryTest {
    private static final Thing mockThing = new Thing("mock-thing");
    private static final Certificate mockCertificate = new Certificate("mock-certificateId");
    private static final Certificate mockCertificate2 = new Certificate("mock-certificate2Id");

    @Mock
    private IotAuthClient mockIotAuthClient;
    private DomainEvents domainEvents;
    private ThingRegistry registry;

    @BeforeEach
    void beforeEach() {
        domainEvents = new DomainEvents();
        registry = new ThingRegistry(mockIotAuthClient, domainEvents);
    }

    @Test
    void GIVEN_validThingAndCertificate_WHEN_isThingAttachedToCertificate_THEN_pass() {
        // positive result
        when(mockIotAuthClient.isThingAttachedToCertificate(any(Thing.class), any(Certificate.class))).thenReturn(true);
        assertTrue(registry.isThingAttachedToCertificate(mockThing, mockCertificate));
        verify(mockIotAuthClient, times(1)).isThingAttachedToCertificate(any(), any());

        // negative result
        reset(mockIotAuthClient);
        when(mockIotAuthClient.isThingAttachedToCertificate(any(Thing.class), any(Certificate.class))).thenReturn(false);
        assertFalse(registry.isThingAttachedToCertificate(mockThing, mockCertificate));
    }

    @Test
    void GIVEN_unreachable_cloud_WHEN_isThingAttachedToCertificate_THEN_return_cached_result() {
        // cache result before going offline
        registry.attachCertificateToThing(mockThing.getThingName(), mockCertificate.getIotCertificateId());

        // go offline
        doThrow(CloudServiceInteractionException.class)
                .when(mockIotAuthClient).isThingAttachedToCertificate(any(), any());

        // verify cached result
        assertTrue(registry.isThingAttachedToCertificate(mockThing, mockCertificate));
        verify(mockIotAuthClient, times(1)).isThingAttachedToCertificate(any(), any());
    }

    @Test
    void GIVEN_offline_initialization_WHEN_isThingAttachedToCertificate_THEN_throws_exception() {
        doThrow(CloudServiceInteractionException.class)
                .when(mockIotAuthClient).isThingAttachedToCertificate(any(), any());

        assertThrows(CloudServiceInteractionException.class, () ->
                registry.isThingAttachedToCertificate(mockThing, mockCertificate));
        verify(mockIotAuthClient, times(1)).isThingAttachedToCertificate(any(), any());
    }

    @Test
    void GIVEN_thingPresentInRegistry_WHEN_getThing_THEN_ThingObjectReturned() {
        registry.attachCertificateToThing(mockThing.getThingName(), mockCertificate.getIotCertificateId());
        Thing thing = registry.getThing(mockThing.getThingName());

        assertThat(thing.getThingName(), is(mockThing.getThingName()));
        assertThat(thing.getAttachedCertificateIds(), contains(mockCertificate.getIotCertificateId()));
    }

    @Test
    void GIVEN_emptyRegistry_WHEN_attachCertificateToThing_THEN_thingEventEmitted() {
        AtomicReference<ThingEvent> thingEvent = new AtomicReference();
        Consumer<ThingEvent> eventListener = (event) -> thingEvent.set(event);
        domainEvents.registerListener(eventListener, ThingEvent.class);

        registry.attachCertificateToThing(mockThing.getThingName(), mockCertificate.getIotCertificateId());

        // ensure event contains the correct information
        ThingEvent event = thingEvent.get();
        assertThat(event, is(notNullValue()));
        assertThat(event.getEventType(), is(ThingEvent.ThingEventType.THING_UPDATED));
        assertThat(event.getThingName(), is(mockThing.getThingName()));
        assertThat(event.getAttachedCertificateIds(), is(notNullValue()));
        assertThat(event.getAttachedCertificateIds(), contains(mockCertificate.getIotCertificateId()));
    }

    @Test
    void GIVEN_thingPresentInRegistry_WHEN_attachCertificateToThing_THEN_thingEventEmitted() {
        AtomicReference<ThingEvent> thingEvent = new AtomicReference();
        Consumer<ThingEvent> eventListener = (event) -> thingEvent.set(event);
        domainEvents.registerListener(eventListener, ThingEvent.class);

        // Initialize registry with Thing + Cert1 and then update
        registry.attachCertificateToThing(mockThing.getThingName(), mockCertificate.getIotCertificateId());
        registry.attachCertificateToThing(mockThing.getThingName(), mockCertificate2.getIotCertificateId());

        // ensure event contains the correct information
        ThingEvent event = thingEvent.get();
        assertThat(event, is(notNullValue()));
        assertThat(event.getEventType(), is(ThingEvent.ThingEventType.THING_UPDATED));
        assertThat(event.getThingName(), is(mockThing.getThingName()));
        assertThat(event.getAttachedCertificateIds(), is(notNullValue()));
        assertThat(event.getAttachedCertificateIds(),
                containsInAnyOrder(mockCertificate.getIotCertificateId(),
                        mockCertificate2.getIotCertificateId()));
    }

    @Test
    void GIVEN_thingWithMultipleCertificates_WHEN_certificateDetached_THEN_thingEventEmitted() {
        AtomicReference<ThingEvent> thingEvent = new AtomicReference();
        Consumer<ThingEvent> eventListener = (event) -> thingEvent.set(event);
        domainEvents.registerListener(eventListener, ThingEvent.class);

        // Initialize registry with Thing with two certs, and then detach one
        registry.attachCertificateToThing(mockThing.getThingName(), mockCertificate.getIotCertificateId());
        registry.attachCertificateToThing(mockThing.getThingName(), mockCertificate2.getIotCertificateId());
        registry.detachCertificateFromThing(mockThing.getThingName(), mockCertificate.getIotCertificateId());

        // ensure event contains the correct information
        ThingEvent event = thingEvent.get();
        assertThat(event, is(notNullValue()));
        assertThat(event.getEventType(), is(ThingEvent.ThingEventType.THING_UPDATED));
        assertThat(event.getThingName(), is(mockThing.getThingName()));
        assertThat(event.getAttachedCertificateIds(), is(notNullValue()));
        assertThat(event.getAttachedCertificateIds(), contains(mockCertificate2.getIotCertificateId()));
    }

    @Test
    void GIVEN_thingPresentInRegistry_WHEN_certificateDetached_THEN_thingEventEmitted() {
        AtomicReference<ThingEvent> thingEvent = new AtomicReference();
        Consumer<ThingEvent> eventListener = (event) -> thingEvent.set(event);
        domainEvents.registerListener(eventListener, ThingEvent.class);

        // Initialize registry with Thing and then detach
        registry.attachCertificateToThing(mockThing.getThingName(), mockCertificate.getIotCertificateId());
        registry.detachCertificateFromThing(mockThing.getThingName(), mockCertificate.getIotCertificateId());

        // ensure event contains the correct information
        ThingEvent event = thingEvent.get();
        assertThat(event, is(notNullValue()));
        assertThat(event.getEventType(), is(ThingEvent.ThingEventType.THING_UPDATED));
        assertThat(event.getThingName(), is(mockThing.getThingName()));
        assertThat(event.getAttachedCertificateIds().size(), is(0));
    }
}
