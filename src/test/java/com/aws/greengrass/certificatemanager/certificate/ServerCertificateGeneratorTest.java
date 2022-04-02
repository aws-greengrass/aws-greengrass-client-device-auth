/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class ServerCertificateGeneratorTest {
    private static final String TEST_PASSPHRASE = "testPassphrase";
    private static final String SUBJECT_PRINCIPAL
            = "CN=testCNC\\=USST\\=WashingtonL\\=SeattleO\\=Amazon.com Inc.OU\\=Amazon Web Services";

    @Mock
    private Consumer<X509Certificate> mockCallback;

    private PublicKey publicKey;
    private CertificateGenerator certificateGenerator;

    @TempDir
    Path tmpPath;

    @BeforeEach
    void setup() throws KeyStoreException, NoSuchAlgorithmException {
        X500Name subject = new X500Name(SUBJECT_PRINCIPAL);
        publicKey = CertificateStore.newRSAKeyPair().getPublic();
        CertificateStore certificateStore = new CertificateStore(tmpPath);
        certificateStore.update(TEST_PASSPHRASE, CertificateStore.CAType.RSA_2048);
        CertificatesConfig certificatesConfig = new CertificatesConfig(Topics.of(new Context(),
                KernelConfigResolver.CONFIGURATION_CONFIG_KEY, null));
        certificateGenerator = new ServerCertificateGenerator(subject, publicKey, mockCallback, certificateStore,
                certificatesConfig);
    }

    @Test
    void GIVEN_ServerCertificateGenerator_WHEN_generateCertificate_THEN_certificate_generated()
            throws Exception {
        certificateGenerator.generateCertificate(Collections::emptyList);

        X509Certificate generatedCert = certificateGenerator.getCertificate();
        assertThat(generatedCert.getSubjectX500Principal().getName(), is(SUBJECT_PRINCIPAL));
        assertThat(generatedCert.getExtendedKeyUsage().get(0), is(KeyPurposeId.id_kp_serverAuth.getId()));
        assertThat(generatedCert.getPublicKey(), is(publicKey));
        verify(mockCallback, times(1)).accept(generatedCert);

        certificateGenerator.generateCertificate(Collections::emptyList);
        X509Certificate secondGeneratedCert = certificateGenerator.getCertificate();
        assertThat(secondGeneratedCert, is(not(generatedCert)));
    }

    @Test
    void GIVEN_ServerCertificateGenerator_WHEN_no_certificate_THEN_shouldRegenerate_returns_true() {
        assertTrue(certificateGenerator.shouldRegenerate());
    }

    @Test
    void GIVEN_ServerCertificateGenerator_WHEN_valid_certificate_THEN_shouldRegenerate_returns_false()
            throws Exception {
        certificateGenerator.generateCertificate(Collections::emptyList);
        assertFalse(certificateGenerator.shouldRegenerate());
    }

    @Test
    void GIVEN_ServerCertificateGenerator_WHEN_expired_certificate_THEN_shouldRegenerate_returns_true()
            throws Exception {
        certificateGenerator.generateCertificate(Collections::emptyList);

        Instant expirationTime = Instant.now().plus(Duration.ofSeconds(CertificatesConfig.DEFAULT_SERVER_CERT_EXPIRY_SECONDS));
        Clock mockClock = Clock.fixed(expirationTime, ZoneId.of("UTC"));
        certificateGenerator.setClock(mockClock);
        assertTrue(certificateGenerator.shouldRegenerate());
    }

    @Test
    void GIVEN_emptyConnectivityInfoList_WHEN_generateCertificate_THEN_certificateContainsLocalhost()
            throws Exception {
        certificateGenerator.generateCertificate(Collections::emptyList);

        X509Certificate generatedCert = certificateGenerator.getCertificate();
        Collection<List<?>> subjectAlternativeNames = generatedCert.getSubjectAlternativeNames();
        assertThat(subjectAlternativeNames, is(notNullValue()));
        assertThat(subjectAlternativeNames.size(), is(1));
        // getSubjectAlternativeNames returns a collection of Lists, each containing
        // the general name type, followed by the name itself. E.g. [2, localhost]
        List<?> firstSAN = (List<?>) subjectAlternativeNames.toArray()[0];
        assertThat(firstSAN.get(1), is("localhost"));
    }
}
