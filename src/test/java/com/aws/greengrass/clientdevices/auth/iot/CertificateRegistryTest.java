/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.infra.ClientCertificateStore;
import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;


@ExtendWith({MockitoExtension.class, GGExtension.class})
class CertificateRegistryTest {
    private static X509Certificate validClientCertificate;
    private static String validClientCertificatePem;

    private Topics configTopic;
    private CertificateRegistry registry;

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
    void beforeEach() throws KeyStoreException {
        configTopic = Topics.of(new Context(), "config", null);
        ClientCertificateStore store = new ClientCertificateStore();
        registry = new CertificateRegistry(RuntimeConfiguration.from(configTopic), store);
    }

    @AfterEach
    void afterEach() throws IOException {
        configTopic.context.close();
    }

    @Test
    void GIVEN_emptyRegistryAndValidPEM_WHEN_getCertificateFromPem_THEN_returnEmptyOptional()
            throws InvalidCertificateException {
        Optional<Certificate> cert = registry.getCertificateFromPem(validClientCertificatePem);
        assertThat(cert.isPresent(), is(false));
    }

    @Test
    void GIVEN_registryWithCertificate_WHEN_getCertificateFromPem_THEN_certificateReturnedWithUnknownStatus()
            throws InvalidCertificateException {
        Certificate newCert = registry.getOrCreateCertificate(validClientCertificatePem);

        Optional<Certificate> cert = registry.getCertificateFromPem(validClientCertificatePem);
        assertThat(cert.isPresent(), is(true));

        assertThat(cert.get().getCertificateId(), equalTo(newCert.getCertificateId()));
        assertThat(cert.get().getStatus(), equalTo(newCert.getStatus()));
        assertThat(cert.get().getStatus(), equalTo(Certificate.Status.UNKNOWN));
    }

    @Test
    void GIVEN_invalidCertificate_WHEN_createCertificate_THEN_exceptionThrown() {
        assertThrows(InvalidCertificateException.class, () -> registry.getOrCreateCertificate("BAD CERT"));
    }

    @Test
    void GIVEN_certificateWithUpdate_WHEN_updateCertificate_THEN_updatedCertificateIsRetrievable()
            throws InvalidCertificateException {
        Certificate newCert = registry.getOrCreateCertificate(validClientCertificatePem);
        Instant now = Instant.now();

        assertThat(newCert.getStatus(), equalTo(Certificate.Status.UNKNOWN));

        newCert.setStatus(Certificate.Status.ACTIVE, now);
        registry.updateCertificate(newCert);

        Optional<Certificate> cert = registry.getCertificateFromPem(validClientCertificatePem);

        assertThat(cert.isPresent(), is(true));
        assertThat(cert.get().getCertificateId(), equalTo(newCert.getCertificateId()));
        assertThat(cert.get().getStatus(), equalTo(Certificate.Status.ACTIVE));
        // asserting on epochMillis since Instant resolution varies with JDK versions
        assertThat(cert.get().getStatusLastUpdated().toEpochMilli(), equalTo(now.toEpochMilli()));
    }

    @Test
    void GIVEN_validCertificate_WHEN_removeCertificate_THEN_certificateIsNotRetrievable()
            throws InvalidCertificateException {
        registry.getOrCreateCertificate(validClientCertificatePem);

        Optional<Certificate> cert = registry.getCertificateFromPem(validClientCertificatePem);
        assertThat(cert.isPresent(), is(true));

        registry.deleteCertificate(cert.get());

        Optional<Certificate> cert2 = registry.getCertificateFromPem(validClientCertificatePem);
        assertThat(cert2.isPresent(), is(false));
    }
}
