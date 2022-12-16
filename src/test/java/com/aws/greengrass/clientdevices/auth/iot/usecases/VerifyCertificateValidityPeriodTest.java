/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.ZoneId;

import static com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers.createClientCertificate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith({MockitoExtension.class})
class VerifyCertificateValidityPeriodTest {
    @Test
    void GIVEN_invalidCertificate_WHEN_verifyCertificateValid_THEN_returnsFalse() {
        VerifyCertificateValidityPeriod useCase = new VerifyCertificateValidityPeriod(Clock.systemUTC());
        assertThat(useCase.apply("FAKE_PEM"), is(false));
    }

    @Test
    void GIVEN_currentTimeIsBeforeCertificateNotBefore_WHEN_verifyCertificateValid_THEN_returnsFalse()
            throws Exception {
        X509Certificate clientCert = createTestClientCertificate();
        Clock fixedClock = Clock.fixed(clientCert.getNotBefore().toInstant().minusSeconds(1), ZoneId.systemDefault());

        VerifyCertificateValidityPeriod useCase = new VerifyCertificateValidityPeriod(fixedClock);
        assertThat(useCase.apply(CertificateHelper.toPem(clientCert)), is(false));
    }

    @Test
    void GIVEN_currentTimeIsWithinCertificateValidityPeriod_WHEN_verifyCertificateValid_THEN_returnsTrue()
            throws Exception {
        X509Certificate clientCert = createTestClientCertificate();
        Clock fixedClock = Clock.fixed(clientCert.getNotBefore().toInstant().plusSeconds(1), ZoneId.systemDefault());

        VerifyCertificateValidityPeriod useCase = new VerifyCertificateValidityPeriod(fixedClock);
        assertThat(useCase.apply(CertificateHelper.toPem(clientCert)), is(true));
    }

    @Test
    void GIVEN_currentTimeIsAfterCertificateNotAfter_WHEN_verifyCertificateValid_THEN_returnsFalse() throws Exception {
        X509Certificate clientCert = createTestClientCertificate();
        Clock fixedClock = Clock.fixed(clientCert.getNotAfter().toInstant().plusSeconds(1), ZoneId.systemDefault());

        VerifyCertificateValidityPeriod useCase = new VerifyCertificateValidityPeriod(fixedClock);
        assertThat(useCase.apply(CertificateHelper.toPem(clientCert)), is(false));
    }

    private X509Certificate createTestClientCertificate() throws Exception {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);
        KeyPair clientKeyPair = CertificateStore.newRSAKeyPair(2048);
        return createClientCertificate(rootCA, "AWS IoT Certificate", clientKeyPair.getPublic(),
                rootKeyPair.getPrivate());
    }
}
