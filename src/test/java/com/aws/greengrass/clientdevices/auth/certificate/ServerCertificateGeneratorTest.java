/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate;

import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
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
    private Topics configurationTopics;
    private CertificateGenerator certificateGenerator;

    @TempDir
    Path tmpPath;

    @BeforeEach
    void setup() throws KeyStoreException, NoSuchAlgorithmException {
        X500Name subject = new X500Name(SUBJECT_PRINCIPAL);
        publicKey = CertificateStore.newRSAKeyPair().getPublic();
        CertificateStore certificateStore = new CertificateStore(tmpPath);
        certificateStore.update(TEST_PASSPHRASE, CertificateStore.CAType.RSA_2048);
        configurationTopics = Topics.of(new Context(), KernelConfigResolver.CONFIGURATION_CONFIG_KEY, null);
        CertificatesConfig certificatesConfig = new CertificatesConfig(configurationTopics);
        certificateGenerator = new ServerCertificateGenerator(subject, publicKey, mockCallback, certificateStore,
                certificatesConfig, Clock.systemUTC());
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    void GIVEN_ServerCertificateGenerator_WHEN_generateCertificate_THEN_certificate_generated()
            throws Exception {
        certificateGenerator.generateCertificate(Collections::emptyList, "test");

        X509Certificate generatedCert = certificateGenerator.getCertificate();
        assertThat(generatedCert.getSubjectX500Principal().getName(), is(SUBJECT_PRINCIPAL));
        assertThat(generatedCert.getExtendedKeyUsage().get(0), is(KeyPurposeId.id_kp_serverAuth.getId()));
        assertThat(generatedCert.getPublicKey(), is(publicKey));
        verify(mockCallback, times(1)).accept(generatedCert);

        certificateGenerator.generateCertificate(Collections::emptyList, "test");
        X509Certificate secondGeneratedCert = certificateGenerator.getCertificate();
        assertThat(secondGeneratedCert, is(not(generatedCert)));
    }

    @Test
    void GIVEN_emptyConnectivityInfoList_WHEN_generateCertificate_THEN_certificateContainsLocalhost()
            throws Exception {
        certificateGenerator.generateCertificate(Collections::emptyList, "test");

        X509Certificate generatedCert = certificateGenerator.getCertificate();
        Collection<List<?>> subjectAlternativeNames = generatedCert.getSubjectAlternativeNames();
        assertThat(subjectAlternativeNames, is(notNullValue()));
        assertThat(subjectAlternativeNames.size(), is(1));
        // getSubjectAlternativeNames returns a collection of Lists, each containing
        // the general name type, followed by the name itself. E.g. [2, localhost]
        List<?> firstSAN = (List<?>) subjectAlternativeNames.toArray()[0];
        assertThat(firstSAN.get(1), is("localhost"));
    }

    @Test
    void GIVEN_ServerCertificateGenerator_WHEN_rotation_disabled_THEN_only_initial_certificate_generated()
            throws CertificateGenerationException {
        configurationTopics.lookup(CertificatesConfig.PATH_DISABLE_CERTIFICATE_ROTATION).withValue(true);

        // initial cert generation and some rotation attempts
        certificateGenerator.generateCertificate(Collections::emptyList, "test");
        certificateGenerator.generateCertificate(Collections::emptyList, "test");
        certificateGenerator.generateCertificate(Collections::emptyList, "test");

        // only the initial cert is generated, no rotation occurs
        verify(mockCallback, times(1)).accept(certificateGenerator.getCertificate());
    }
}
