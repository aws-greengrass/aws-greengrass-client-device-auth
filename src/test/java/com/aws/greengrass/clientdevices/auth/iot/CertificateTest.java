/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;


import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;

import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class CertificateTest {
    private static X509Certificate validClientCertificate;
    private static String validClientCertificatePem;

    @BeforeAll
    static void beforeAll()
            throws CertificateException, NoSuchAlgorithmException, OperatorCreationException, IOException {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        KeyPair clientKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);

        validClientCertificate =
                CertificateTestHelpers.createClientCertificate(rootCA, "Client", clientKeyPair.getPublic(),
                        rootKeyPair.getPrivate());
        validClientCertificatePem = CertificateHelper.toPem(validClientCertificate);
    }

    @BeforeEach
    void beforeEach() {
        Certificate.updateMetadataTrustDurationMinutes(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES);
    }

    private Runnable mockInstant(long expectedMillis) {
        Clock spyClock = spy(Clock.class);
        MockedStatic<Clock> clockMock;
        clockMock = mockStatic(Clock.class);
        clockMock.when(Clock::systemUTC).thenReturn(spyClock);
        when(spyClock.instant()).thenReturn(Instant.ofEpochMilli(expectedMillis));

        return clockMock::close;
    }

    @Test
    void GIVEN_validActiveCertificate_WHEN_isActive_THEN_returnTrue() throws InvalidCertificateException {
        Certificate cert = Certificate.fromPem(validClientCertificatePem);
        cert.setStatus(Certificate.Status.ACTIVE);
        assertTrue(cert.isActive());
    }

    @Test
    void GIVEN_disabledTrustVerificationAndActiveCertificate_WHEN_isActive_THEN_returnTrue() throws InvalidCertificateException {
        // update trust duration to zero, indicating disabled trust verification
        Certificate.updateMetadataTrustDurationMinutes(0);
        Certificate cert = Certificate.fromPem(validClientCertificatePem);
        cert.setStatus(Certificate.Status.ACTIVE);
        assertTrue(cert.isActive());
    }

    @Test
    void GIVEN_enabledTrustVerificationAndUnexpiredActiveCertificate_WHEN_isActive_THEN_returnTrue() throws InvalidCertificateException {
        // update trust duration to non-zero, indicating enabled trust verification
        Certificate.updateMetadataTrustDurationMinutes(5);
        Instant now = Instant.now();
        Runnable resetClock = mockInstant(now.toEpochMilli());

        Certificate cert = Certificate.fromPem(validClientCertificatePem);
        cert.setStatus(Certificate.Status.ACTIVE);

        // verify certificate ACTIVE status before trust duration has passed
        resetClock.run();
        Instant oneMinuteLater = now.plusSeconds(60L);
        resetClock = mockInstant(oneMinuteLater.toEpochMilli());
        assertTrue(cert.isActive());

        resetClock.run();
    }

    @Test
    void GIVEN_enabledTrustVerificationAndExpiredActiveCertificate_WHEN_isActive_THEN_returnFalse() throws InvalidCertificateException {
        // update trust duration to non-zero, indicating enabled trust verification
        Certificate.updateMetadataTrustDurationMinutes(1);
        Instant now = Instant.now();
        Runnable resetClock = mockInstant(now.toEpochMilli());

        Certificate cert = Certificate.fromPem(validClientCertificatePem);
        cert.setStatus(Certificate.Status.ACTIVE);

        // verify certificate ACTIVE status after trust duration has passed
        resetClock.run();
        Instant anHourLater = now.plusSeconds(60L * 60L);
        resetClock = mockInstant(anHourLater.toEpochMilli());
        assertFalse(cert.isActive());

        resetClock.run();
    }
}
