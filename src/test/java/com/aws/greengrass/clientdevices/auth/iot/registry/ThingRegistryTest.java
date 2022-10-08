/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateFake;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
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
    private static final Thing mockThing = Thing.of(mockThingName);
    private static Certificate mockCertificate;

    @Mock
    private IotAuthClient mockIotAuthClient;
    private Topics configTopics;
    private DomainEvents domainEvents;
    private ThingRegistry registry;

    @BeforeEach
    void beforeEach() throws InvalidCertificateException {
        domainEvents = new DomainEvents();
        configTopics = Topics.of(new Context(), "config", null);
        registry = new ThingRegistry(mockIotAuthClient, domainEvents, RuntimeConfiguration.from(configTopics));
        mockCertificate = CertificateFake.of("mock-certificateId");
    }

    @AfterEach
    void afterEach() throws IOException {
        configTopics.context.close();
    }

    @Test
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    void GIVEN_emptyRegistry_WHEN_createThing_THEN_thingIsAddedToRegistry() {
        Thing createdThing = registry.createThing(mockThingName);
        Thing retrievedThing = registry.getThing(mockThingName);

        assertThat(createdThing.getThingName(), is(mockThingName));
        assertThat(createdThing.getAttachedCertificateIds(), equalTo(Collections.emptyMap()));

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

    @Test
    void GIVEN_validThingAndCertificate_WHEN_isThingAttachedToCertificate_THEN_pass() {
        // TODO: This test should be re-written since isThingAttachedToCertificate modifies registry state
        // negative result
        when(mockIotAuthClient.isThingAttachedToCertificate(any(Thing.class), any(Certificate.class))).thenReturn(false);
        assertFalse(registry.isThingAttachedToCertificate(mockThing, mockCertificate));

        // positive result
        reset(mockIotAuthClient);
        when(mockIotAuthClient.isThingAttachedToCertificate(any(Thing.class), any(Certificate.class))).thenReturn(true);
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
}
