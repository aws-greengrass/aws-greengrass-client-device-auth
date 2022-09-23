/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class ThingTest {
    @Test
    void GIVEN_validThingName_WHEN_Thing_THEN_objectIsCreated() {
        Assertions.assertDoesNotThrow(() -> Thing.of("abcdefghijklmnopqrstuvwxyz:_-"));
        Assertions.assertDoesNotThrow(() -> Thing.of("ABCDEFGHIJKLMNOPQRSTUXWXYZ"));
        Assertions.assertDoesNotThrow(() -> Thing.of("0123456789"));
    }

    @Test
    void GIVEN_emptyThingName_WHEN_Thing_THEN_exceptionThrown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of(""));
    }

    @Test
    void GIVEN_thingNameWithInvalidCharacters_WHEN_Thing_THEN_exceptionThrown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of("Thing!"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of("Thing@"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of("Thing#"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of("Thing$"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of("Thing%"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of("Thing^"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of("Thing&"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of("Thing*"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of("Thing("));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of("Thing)"));
    }

    @Test
    void GIVEN_invalidVersion_WHEN_Thing_THEN_exceptionThrown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Thing.of(-1, "Thing"));
        Assertions.assertDoesNotThrow(() -> Thing.of(0, "Thing"));
    }

    @Test
    void GIVEN_thing_WHEN_attachCertificate_THEN_certAttachedAndDirtyBitSet() {
        Thing thing = Thing.of(0, "Thing");
        thing.attachCertificate("cert-id");

        assertThat(thing.isModified(), is(true));
        List<String> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds, equalTo(Collections.singletonList("cert-id")));
    }

    @Test
    void GIVEN_thingWithCertificate_WHEN_attachSameCertificate_THEN_noChange() {
        Thing thing = Thing.of(0, "Thing", Collections.singletonList("cert-id"));
        thing.attachCertificate("cert-id");

        assertThat(thing.isModified(), is(false));
        List<String> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds, equalTo(Collections.singletonList("cert-id")));
    }

    @Test
    void GIVEN_thingWithoutCertificate_WHEN_detachCertificate_THEN_noChange() {
        Thing thing = Thing.of(0, "Thing");
        thing.detachCertificate("cert-id");

        assertThat(thing.isModified(), is(false));
        List<String> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds, equalTo(Collections.emptyList()));
    }

    @Test
    void GIVEN_thingWithCertificate_WHEN_detachCertificate_THEN_certDetachedAndDirtyBitSet() {
        Thing thing = Thing.of(0, "Thing", Collections.singletonList("cert-id"));
        thing.detachCertificate("cert-id");

        assertThat(thing.isModified(), is(true));
        List<String> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds, equalTo(Collections.emptyList()));
    }

    @Test
    void testEquals() {
        Thing version0_Thing_NoList = Thing.of(0, "Thing");
        Thing version0_Thing2_NoList = Thing.of(0, "Thing2", Collections.emptyList());
        Thing version0_Thing_EmptyList = Thing.of(0, "Thing", Collections.emptyList());
        Thing version1_Thing_SingleCert = Thing.of(1, "Thing", Collections.singletonList("certId"));
        Thing version1_Thing_SingleCert_copy = Thing.of(1, "Thing", Collections.singletonList("certId"));
        Thing version2_Thing_SingleCert = Thing.of(2, "Thing", Collections.singletonList("certId"));
        Thing version3_Thing_CertA = Thing.of(3, "Thing", Collections.singletonList("CertA"));
        Thing version3_Thing_CertB = Thing.of(3, "Thing", Collections.singletonList("CertB"));
        Thing version4_Thing_MultiCert = Thing.of(4, "Thing", Arrays.asList("Cert1", "Cert2"));
        Thing version4_Thing_MultiCert_copy = Thing.of(4, "Thing", Arrays.asList("Cert1", "Cert2"));

        assertThat(version0_Thing_NoList, equalTo(version0_Thing_EmptyList));
        assertThat(version0_Thing_NoList, not(equalTo(version0_Thing2_NoList)));
        assertThat(version1_Thing_SingleCert, equalTo(version1_Thing_SingleCert_copy));
        assertThat(version1_Thing_SingleCert, not(equalTo(version2_Thing_SingleCert)));
        assertThat(version3_Thing_CertA, not(equalTo(version3_Thing_CertB)));
        assertThat(version4_Thing_MultiCert, equalTo(version4_Thing_MultiCert_copy));
    }
}
