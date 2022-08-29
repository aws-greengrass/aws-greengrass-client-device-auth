/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;


import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Mock
    private IotAuthClient mockIotAuthClient;
    @Mock
    private RegistryRefreshScheduler mockRefreshScheduler;

    private ThingRegistry registry;

    @BeforeEach
    void beforeEach() {
        registry = new ThingRegistry(mockIotAuthClient, mockRefreshScheduler);
    }

    @Test
    void GIVEN_valid_thing_and_certificate_WHEN_isThingAttachedToCertificate_THEN_pass() {
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
        when(mockIotAuthClient.isThingAttachedToCertificate(any(Thing.class), any(Certificate.class))).thenReturn(true);
        assertTrue(registry.isThingAttachedToCertificate(mockThing, mockCertificate));

        // go offline
        reset(mockIotAuthClient);
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
    void GIVEN_certRegistry_WHEN_refreshRegistry_THEN_stale_entries_removed() {
        String mockThing1 = "thing1";
        String mockThing2 = "thing2";
        String mockCertId1 = "mockCertId1";
        String mockCertId2 = "mockCertId2";
        String mockCertId3 = "mockCertId3";
        Set<CertificateEntry> certSet1 = new HashSet<>();
        Set<CertificateEntry> certSet2 = new HashSet<>();

        CertificateEntry validEntry = new CertificateEntry(Instant.now().plusSeconds(10L),
                null, mockCertId1);
        CertificateEntry invalidEntry1 = new CertificateEntry(Instant.now().minusSeconds(10L),
                null, mockCertId2);
        CertificateEntry invalidEntry2 = new CertificateEntry(Instant.now(),
                null, mockCertId3);

        certSet1.add(validEntry);
        certSet1.add(invalidEntry1);
        certSet2.add(invalidEntry2);

        registry.getRegistry().put(mockThing1, certSet1);
        registry.getRegistry().put(mockThing2, certSet2);

        assertThat(registry.getRegistry().size(), is(2));

        registry.refreshRegistry();
        assertThat(registry.getRegistry().size(), is(1));
        assertNotNull(registry.getRegistry().get(mockThing1));
        assertThat(registry.getRegistry().get(mockThing1).size(), is(1));
        assertTrue(registry.getRegistry().get(mockThing1).contains(validEntry));
        assertNull(registry.getRegistry().get(mockThing2));
    }
}
