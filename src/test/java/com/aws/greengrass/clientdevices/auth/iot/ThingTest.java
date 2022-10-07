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
    void GIVEN_thing_WHEN_attachCertificate_THEN_certAttachedAndDirtyBitSet() {
        Thing thing = Thing.of("Thing");
        thing.attachCertificate("cert-id");

        assertThat(thing.isModified(), is(true));
        List<String> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds, equalTo(Collections.singletonList("cert-id")));
    }

    @Test
    void GIVEN_thingWithCertificate_WHEN_attachSameCertificate_THEN_noChange() {
        Thing thing = Thing.of("Thing", Collections.singletonList("cert-id"));
        thing.attachCertificate("cert-id");

        assertThat(thing.isModified(), is(false));
        List<String> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds, equalTo(Collections.singletonList("cert-id")));
    }

    @Test
    void GIVEN_thingWithoutCertificate_WHEN_detachCertificate_THEN_noChange() {
        Thing thing = Thing.of("Thing");
        thing.detachCertificate("cert-id");

        assertThat(thing.isModified(), is(false));
        List<String> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds, equalTo(Collections.emptyList()));
    }

    @Test
    void GIVEN_thingWithCertificate_WHEN_detachCertificate_THEN_certDetachedAndDirtyBitSet() {
        Thing thing = Thing.of("Thing", Collections.singletonList("cert-id"));
        thing.detachCertificate("cert-id");

        assertThat(thing.isModified(), is(true));
        List<String> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds, equalTo(Collections.emptyList()));
    }

    @Test
    void testEquals() {
        Thing Thing_NoList = Thing.of("Thing");
        Thing Thing2_NoList = Thing.of("Thing2", Collections.emptyList());
        Thing Thing_EmptyList = Thing.of("Thing", Collections.emptyList());
        Thing Thing_SingleCert = Thing.of("Thing", Collections.singletonList("certId"));
        Thing Thing_SingleCert_copy = Thing.of("Thing", Collections.singletonList("certId"));
        Thing Thing_CertA = Thing.of("Thing", Collections.singletonList("CertA"));
        Thing Thing_CertB = Thing.of("Thing", Collections.singletonList("CertB"));
        Thing Thing_MultiCert = Thing.of("Thing", Arrays.asList("Cert1", "Cert2"));
        Thing Thing_MultiCert_copy = Thing.of("Thing", Arrays.asList("Cert1", "Cert2"));

        assertThat(Thing_NoList, equalTo(Thing_EmptyList));
        assertThat(Thing_NoList, not(equalTo(Thing2_NoList)));
        assertThat(Thing_SingleCert, equalTo(Thing_SingleCert_copy));
        assertThat(Thing_SingleCert, equalTo(Thing_SingleCert));
        assertThat(Thing_CertA, not(equalTo(Thing_CertB)));
        assertThat(Thing_MultiCert, equalTo(Thing_MultiCert_copy));
    }
}
