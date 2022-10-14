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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @AfterEach
    void afterEach() {
        Certificate.updateMetadataTrustDurationHours(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS);
    }

    @Test
    void GIVEN_validActiveCertificate_WHEN_isActive_THEN_returnTrue() throws InvalidCertificateException {
        Certificate cert = Certificate.fromPem(validClientCertificatePem);
        cert.setStatus(Certificate.Status.ACTIVE);
        assertTrue(cert.isActive());
    }

    @Test
    void GIVEN_expiredActiveCertificate_WHEN_isActive_THEN_returnFalse() throws InvalidCertificateException {
        // update trust duration to zero, indicating not to trust any metadata
        Certificate.updateMetadataTrustDurationHours(0);
        Certificate cert = Certificate.fromPem(validClientCertificatePem);
        cert.setStatus(Certificate.Status.ACTIVE);
        assertFalse(cert.isActive());
    }
}
