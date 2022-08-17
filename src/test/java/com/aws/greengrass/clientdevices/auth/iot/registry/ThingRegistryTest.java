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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private ThingRegistry registry;

    @BeforeEach
    void beforeEach() {
        registry = new ThingRegistry(mockIotAuthClient);
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
    void GIVEN_offline_initialization_WHEN_isThingAttachedToCertificate_THEN_return_false_by_default() {
        doThrow(CloudServiceInteractionException.class)
                .when(mockIotAuthClient).isThingAttachedToCertificate(any(), any());

        assertFalse(registry.isThingAttachedToCertificate(mockThing, mockCertificate));
        verify(mockIotAuthClient, times(1)).isThingAttachedToCertificate(any(), any());
    }

}
