/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.utils.ImmutableMap;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;

import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class ThingTest {
    private static final String mockThingName = "mock-thing";
    private static final String mockCertId = "mock-cert-id";
    private static Map<String, Instant> mockCertIdMap = ImmutableMap.of(mockCertId, Instant.now());

    @BeforeEach
    void beforeEach() {
        Thing.updateMetadataTrustDurationMinutes(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES);
    }

    private Runnable mockInstant(long expectedMillis) {
        Clock spyClock = spy(Clock.class);
        MockedStatic<Clock> clockMock;
        clockMock = mockStatic(Clock.class, withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        clockMock.when(Clock::systemUTC).thenReturn(spyClock);
        when(spyClock.instant()).thenReturn(Instant.ofEpochMilli(expectedMillis));

        return clockMock::close;
    }

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
        Thing thing = Thing.of(mockThingName);
        thing.attachCertificate(mockCertId);

        assertThat(thing.isModified(), is(true));
        Map<String, Instant> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds.get(mockCertId), isA(Instant.class));
    }

    @Test
    void GIVEN_thingWithCertificate_WHEN_attachSameCertificate_THEN_timestampUpdated() {
        Thing thing = Thing.of(mockThingName, ImmutableMap.of(mockCertId, Instant.EPOCH));
        Instant prevInstant = thing.getAttachedCertificateIds().get(mockCertId);
        assertNotNull(prevInstant);

        // Re-attach certificate
        thing.attachCertificate(mockCertId);
        assertThat(thing.isModified(), is(true));

        Map<String, Instant> certIds = thing.getAttachedCertificateIds();
        Instant updatedInstant = certIds.get(mockCertId);
        assertNotNull(updatedInstant);
        assertTrue(updatedInstant.isAfter(prevInstant));
    }

    @Test
    void GIVEN_thingWithoutCertificate_WHEN_detachCertificate_THEN_noChange() {
        Thing thing = Thing.of(mockThingName);
        thing.detachCertificate(mockCertId);

        assertThat(thing.isModified(), is(false));
        Map<String, Instant> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds.size(), is(0));
    }

    @Test
    void GIVEN_thingWithCertificate_WHEN_detachCertificate_THEN_certDetachedAndDirtyBitSet() {
        Thing thing = Thing.of(mockThingName, mockCertIdMap);
        thing.detachCertificate(mockCertId);

        assertThat(thing.isModified(), is(true));
        Map<String, Instant> certIds = thing.getAttachedCertificateIds();
        assertThat(certIds.size(), is(0));
    }

    @Test
    void testEquals() {
        Instant now = Instant.now();
        Instant later = now.plus(1, ChronoUnit.DAYS);
        Thing Thing_NoList = Thing.of("Thing");
        Thing Thing2_NoList = Thing.of("Thing2", Collections.emptyMap());
        Thing Thing_EmptyList = Thing.of("Thing", Collections.emptyMap());
        Thing Thing_SingleCert = Thing.of("Thing", Collections.singletonMap("certId", now));
        Thing Thing_SingleCert_copy = Thing.of("Thing", Collections.singletonMap("certId", now));
        Thing Thing_SingleCert_newer = Thing.of("Thing", Collections.singletonMap("certId", later));
        Thing Thing_CertA = Thing.of("Thing", Collections.singletonMap("CertA", now));
        Thing Thing_CertB = Thing.of("Thing", Collections.singletonMap("CertB", now));
        Thing Thing_MultiCert = Thing.of("Thing", ImmutableMap.of("Cert1", now, "Cert2", now));
        Thing Thing_MultiCert_copy = Thing.of("Thing", ImmutableMap.of("Cert1", now, "Cert2", now));

        assertThat(Thing_NoList, equalTo(Thing_EmptyList));
        assertThat(Thing_NoList, not(equalTo(Thing2_NoList)));
        assertThat(Thing_SingleCert, equalTo(Thing_SingleCert_copy));
        assertThat(Thing_SingleCert, equalTo(Thing_SingleCert));
        assertThat(Thing_SingleCert, not(equalTo(Thing_SingleCert_newer)));
        assertThat(Thing_CertA, not(equalTo(Thing_CertB)));
        assertThat(Thing_MultiCert, equalTo(Thing_MultiCert_copy));
    }

    @Test
    void GIVEN_thingWithValidActiveCertificate_WHEN_isCertificateAttached_THEN_returnTrue() {
        Thing thing = Thing.of(mockThingName);
        thing.attachCertificate(mockCertId);
        assertTrue(thing.isCertificateAttached(mockCertId));
    }

    @Test
    void GIVEN_disabledTrustVerificationAndActiveCertificate_WHEN_isCertificateAttached_THEN_returnTrue() {
        // update trust duration to zero, indicating disabled trust verification
        Thing.updateMetadataTrustDurationMinutes(0);
        Thing thing = Thing.of(mockThingName);
        thing.attachCertificate(mockCertId);
        assertTrue(thing.isCertificateAttached(mockCertId));
    }

    @Test
    void GIVEN_enabledTrustVerificationAndUnexpiredCertificate_WHEN_isCertificateAttached_THEN_returnTrue() {
        // update trust duration to non-zero, indicating enabled trust verification
        Thing.updateMetadataTrustDurationMinutes(5);
        Instant now = Instant.now();
        Runnable resetClock = mockInstant(now.toEpochMilli());

        Thing thing = Thing.of(mockThingName);
        thing.attachCertificate(mockCertId);

        // verify certificate attachment before trust duration has passed
        resetClock.run();
        Instant oneMinuteLater = now.plusSeconds(60L);
        resetClock = mockInstant(oneMinuteLater.toEpochMilli());
        assertTrue(thing.isCertificateAttached(mockCertId));

        resetClock.run();
    }

    @Test
    void GIVEN_enabledTrustVerificationAndTrustExpiredCertificate_WHEN_isCertificateAttached_THEN_returnFalse() {
        // update trust duration to non-zero, indicating enabled trust verification
        Thing.updateMetadataTrustDurationMinutes(1);
        Instant now = Instant.now();
        Runnable resetClock = mockInstant(now.toEpochMilli());

        Thing thing = Thing.of(mockThingName);
        thing.attachCertificate(mockCertId);

        // verify certificate attachment after trust duration has passed
        resetClock.run();
        Instant anHourLater = now.plusSeconds(60L * 60L);
        resetClock = mockInstant(anHourLater.toEpochMilli());
        assertFalse(thing.isCertificateAttached(mockCertId));

        resetClock.run();
    }
}
