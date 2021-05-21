/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

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
import java.util.Collections;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class})
public class ClientCertificateGeneratorTest {
    private static final String TEST_PASSPHRASE = "testPassphrase";
    private static final String SUBJECT_PRINCIPAL
            = "CN=testCNC\\=USST\\=WashingtonL\\=SeattleO\\=Amazon.com Inc.OU\\=Amazon Web Services";

    @Mock
    private Consumer<X509Certificate[]> mockCallback;

    private PublicKey publicKey;
    private CertificateGenerator certificateGenerator;
    private CertificateStore certificateStore;

    @TempDir
    Path tmpPath;

    @BeforeEach
    public void setup() throws KeyStoreException, NoSuchAlgorithmException {
        X500Name subject = new X500Name(SUBJECT_PRINCIPAL);
        publicKey = CertificateStore.newRSAKeyPair().getPublic();
        certificateStore = new CertificateStore(tmpPath);
        certificateStore.update(TEST_PASSPHRASE, CertificateStore.CAType.RSA_2048);
        certificateGenerator = new ClientCertificateGenerator(subject, publicKey, mockCallback, certificateStore);
    }

    @Test
    public void GIVEN_ClientCertificateGenerator_WHEN_generateCertificate_THEN_certificate_generated()
            throws Exception {
        certificateGenerator.generateCertificate(Collections::emptyList);

        X509Certificate generatedCert = certificateGenerator.getCertificate();
        assertThat(generatedCert.getSubjectX500Principal().getName(), is(SUBJECT_PRINCIPAL));
        assertThat(new KeyPurposeId(generatedCert.getExtendedKeyUsage().get(0)), is(KeyPurposeId.id_kp_clientAuth));
        assertThat(generatedCert.getPublicKey(), is(publicKey));
        verify(mockCallback, times(1))
                .accept(new X509Certificate[]{generatedCert, certificateStore.getCACertificate()});

        certificateGenerator.generateCertificate(Collections::emptyList);
        X509Certificate secondGeneratedCert = certificateGenerator.getCertificate();
        assertThat(secondGeneratedCert, is(not(generatedCert)));
    }
}
