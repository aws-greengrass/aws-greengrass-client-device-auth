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
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
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
    private static final String mockThingName = "mock-thing";
    private static final Thing mockThing = Thing.of("mock-thing");
    private static final Certificate mockCertificate = new Certificate("mock-certificateId");

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
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    void GIVEN_emptyRegistry_WHEN_createThing_THEN_thingIsAddedToRegistry() {
        Thing createdThing = registry.createThing(mockThingName);
        Thing retrievedThing = registry.getThing(mockThingName);

        assertThat(createdThing.getThingName(), is(mockThingName));
        assertThat(createdThing.getVersion(), is(1));
        assertThat(createdThing.getAttachedCertificateIds(), equalTo(Collections.emptyList()));

        assertThat(createdThing, equalTo(retrievedThing));
        // IMPORTANT! Ensure a copy is returned. It is incorrect to call the equals method for this
        assertThat(createdThing != retrievedThing, is(true));

        // TODO: check ThingUpdated event
    }

    @Test
    void GIVEN_unmodifiedThing_WHEN_updateThing_THEN_oldThingReturned() {
        Thing createdThing = registry.createThing(mockThingName);
        Thing returnedThing = registry.updateThing(createdThing);
        assertThat(createdThing, equalTo(returnedThing));

        // TODO: no update event
    }

    @Disabled
    @Test
    void GIVEN_staleThing_WHEN_updateThing_THEN_updateRejected() {
        // TODO
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
        Thing thing = registry.createThing(mockThingName);
        thing.attachCertificate(mockCertificate.getIotCertificateId());
        Thing updatedThing = registry.updateThing(thing);

        // go offline
        doThrow(CloudServiceInteractionException.class)
                .when(mockIotAuthClient).isThingAttachedToCertificate(any(), any());

        // verify cached result
        assertTrue(registry.isThingAttachedToCertificate(updatedThing, mockCertificate));
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
}
