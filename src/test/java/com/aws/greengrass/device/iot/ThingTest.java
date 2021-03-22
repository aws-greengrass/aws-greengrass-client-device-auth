/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;

@ExtendWith({MockitoExtension.class})
public class ThingTest {
    @Mock
    private IotControlPlaneBetaClient mockBetaClient;

    @Test
    public void GIVEN_validThingName_WHEN_Thing_THEN_objectIsCreated() {
        Assertions.assertDoesNotThrow(() -> new Thing("abcdefghijklmnopqrstuvwxyz:_-"));
        Assertions.assertDoesNotThrow(() -> new Thing("ABCDEFGHIJKLMNOPQRSTUXWXYZ"));
        Assertions.assertDoesNotThrow(() -> new Thing("0123456789"));
    }

    @Test
    public void GIVEN_emptyThingName_WHEN_Thing_THEN_exceptionThrown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing(""));
    }

    @Test
    public void GIVEN_thingNameWithInvalidCharacters_WHEN_Thing_THEN_exceptionThrown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing!"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing@"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing#"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing$"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing%"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing^"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing&"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing*"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing("));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing)"));
    }

    @Test
    public void GIVEN_thingWithoutCert_WHEN_isCertificateAttached_THEN_false() {
        Thing thing = new Thing("testThing");
        Certificate certificate = new Certificate("certPem");

        Mockito.when(mockBetaClient.listThingCertificatePrincipals(Mockito.any()))
                .thenReturn(new ArrayList<>());

        Assertions.assertFalse(thing.isCertificateAttached(certificate, mockBetaClient));
    }

    @Test
    public void GIVEN_thingWithMatchingCert_WHEN_isCertificateAttached_THEN_true() {
        Thing thing = new Thing("testThing");
        Certificate certificate = new Certificate("certPem");

        Mockito.when(mockBetaClient.listThingCertificatePrincipals(Mockito.any()))
                .thenReturn(new ArrayList<>(Arrays.asList("12345")));
        Mockito.when(mockBetaClient.downloadSingleDeviceCertificate("12345"))
                .thenReturn("certPem");

        Assertions.assertTrue(thing.isCertificateAttached(certificate, mockBetaClient));
    }

    @Test
    public void GIVEN_thingWithMisMatchingCert_WHEN_isCertificateAttached_THEN_false() {
        Thing thing = new Thing("testThing");
        Certificate certificate = new Certificate("mismatchedCertPem");

        Mockito.when(mockBetaClient.listThingCertificatePrincipals(Mockito.any()))
                .thenReturn(new ArrayList<>(Arrays.asList("12345")));
        Mockito.when(mockBetaClient.downloadSingleDeviceCertificate("12345"))
                .thenReturn("certPem");

        Assertions.assertFalse(thing.isCertificateAttached(certificate, mockBetaClient));
    }
}
