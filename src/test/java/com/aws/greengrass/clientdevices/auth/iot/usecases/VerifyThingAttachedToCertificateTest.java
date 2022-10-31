/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.VerifyThingAttachedToCertificateDTO;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class VerifyThingAttachedToCertificateTest {
    @Mock
    private IotAuthClient mockIotAuthClient;
    @Mock
    private NetworkState mockNetworkState;
    @Mock
    private ThingRegistry mockThingRegistry;
    private VerifyThingAttachedToCertificate verifyThingAttachedToCertificate;

    @BeforeEach
    void beforeEach() {
        verifyThingAttachedToCertificate = new VerifyThingAttachedToCertificate(mockIotAuthClient,
                mockThingRegistry, mockNetworkState);
    }

    @Test
    void GIVEN_validDtoAndNetworkUp_WHEN_verifyThingAttachedToCertificate_THEN_returnCloudResult() {
        Boolean expectedCloudResult = true;
        Thing mockThing = Thing.of("mock-thing");
        String mockCertId = "cert-id";
        VerifyThingAttachedToCertificateDTO dto =
                new VerifyThingAttachedToCertificateDTO(mockThing.getThingName(), mockCertId);

        when(mockNetworkState.getConnectionStateFromMqtt()).thenReturn(NetworkState.ConnectionState.NETWORK_UP);
        when(mockIotAuthClient.isThingAttachedToCertificate(any(), anyString())).thenReturn(expectedCloudResult);
        when(mockThingRegistry.getThing(mockThing.getThingName())).thenReturn(mockThing);

        assertThat(verifyThingAttachedToCertificate.apply(dto), is(expectedCloudResult));
        verify(mockIotAuthClient, times(1)).isThingAttachedToCertificate(mockThing, mockCertId);
    }

    @Test
    void GIVEN_validDtoAndNetworkDown_WHEN_verifyThingAttachedToCertificate_THEN_returnLocalResult() {
        Thing mockThing = Thing.of("mock-thing");
        String mockCertId = "cert-id";
        mockThing.attachCertificate(mockCertId);
        VerifyThingAttachedToCertificateDTO dto =
                new VerifyThingAttachedToCertificateDTO(mockThing.getThingName(), mockCertId);

        when(mockNetworkState.getConnectionStateFromMqtt()).thenReturn(NetworkState.ConnectionState.NETWORK_DOWN);
        when(mockThingRegistry.getThing(mockThing.getThingName())).thenReturn(mockThing);

        assertThat(verifyThingAttachedToCertificate.apply(dto), is(true));
        verify(mockIotAuthClient, times(0)).isThingAttachedToCertificate(any(), anyString());
    }

    @Test
    void GIVEN_networkUpButFailedCloudCall_WHEN_verifyThingAttachedToCertificate_THEN_returnLocalResult() {
        Thing mockThing = Thing.of("mock-thing");
        String mockCertId = "cert-id";
        mockThing.attachCertificate(mockCertId);
        VerifyThingAttachedToCertificateDTO dto =
                new VerifyThingAttachedToCertificateDTO(mockThing.getThingName(), mockCertId);

        when(mockNetworkState.getConnectionStateFromMqtt()).thenReturn(NetworkState.ConnectionState.NETWORK_UP);
        doThrow(CloudServiceInteractionException.class)
                .when(mockIotAuthClient).isThingAttachedToCertificate(any(), anyString());
        when(mockThingRegistry.getThing(mockThing.getThingName())).thenReturn(mockThing);

        assertThat(verifyThingAttachedToCertificate.apply(dto), is(true));
        verify(mockIotAuthClient, times(1)).isThingAttachedToCertificate(mockThing, mockCertId);
    }
}
