/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.certificatemanager.certificate.CertificateStore.CAType;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CertificateHelperTest {
    private static final String TEST_PASSPHRASE = "testPassphrase";
    private static final String TEST_CN = "testCN";
    private static final String EXPECTED_ISSUER_PRINCIPAL
            = "CN=Greengrass Core CA,L=Seattle,ST=Washington,OU=Amazon Web Services,O=Amazon.com Inc.,C=US";
    private static final String EXPECTED_SUBJECT_PRINCIPAL
            = "CN=testCN,C=US,ST=Washington,L=Seattle,O=Amazon.com Inc.,OU=Amazon Web Services";
    private static final String RSA_CERT_SIG_ALG = "SHA256WITHRSA";
    private static final String ECDSA_CERT_SIG_ALG = "SHA256WITHECDSA";

    private CertificateStore certificateStore;

    @TempDir
    Path tmpPath;

    @BeforeEach
    public void beforeEach() {
        certificateStore = new CertificateStore(tmpPath);
    }

    private X509Certificate signServerCSRWithCA(String csr, List<String> connectivityInfo) throws Exception {
        Instant now = Instant.now();
        PKCS10CertificationRequest pkcs10CertificationRequest =
                CertificateHelper.getPKCS10CertificationRequestFromPem(csr);
        JcaPKCS10CertificationRequest jcaRequest = new JcaPKCS10CertificationRequest(pkcs10CertificationRequest);
        X509Certificate certificate = CertificateHelper.signServerCertificateRequest(
                certificateStore.getCACertificate(),
                certificateStore.getCAPrivateKey(),
                jcaRequest.getSubject(),
                jcaRequest.getPublicKey(),
                connectivityInfo,
                Date.from(now),
                Date.from(now.plusSeconds(60)));

        assertThat(certificate.getIssuerX500Principal().getName(), equalTo(EXPECTED_ISSUER_PRINCIPAL));
        assertThat(certificate.getSubjectX500Principal().getName(), equalTo(EXPECTED_SUBJECT_PRINCIPAL));
        assertThat(new KeyPurposeId(certificate.getExtendedKeyUsage().get(0)), equalTo(KeyPurposeId.id_kp_serverAuth));

        return certificate;
    }

    @Test
    public void GIVEN_valid_rsa_csr_and_rsa_ca_WHEN_signServerCertificateRequest_THEN_return_valid_certificate()
            throws Exception {
        certificateStore.update(TEST_PASSPHRASE, CAType.RSA_2048);

        KeyPair kp = CertificateStore.newRSAKeyPair();
        String csr = CertificateRequestGenerator.createCSR(kp, TEST_CN, null, null);

        X509Certificate certificate = signServerCSRWithCA(csr, Collections.emptyList());
        assertThat(certificate.getSigAlgName(), equalTo(RSA_CERT_SIG_ALG));
    }

    @Test
    public void GIVEN_valid_ec_csr_and_ec_ca_WHEN_signServerCertificateRequest_THEN_return_valid_certificate()
            throws Exception {
        certificateStore.update(TEST_PASSPHRASE, CAType.ECDSA_P256);

        KeyPair kp = CertificateStore.newECKeyPair();
        String csr = CertificateRequestGenerator.createCSR(kp, TEST_CN, null, null);

        X509Certificate certificate = signServerCSRWithCA(csr, Collections.emptyList());
        assertThat(certificate.getSigAlgName(), equalTo(ECDSA_CERT_SIG_ALG));
    }

    @Test
    public void GIVEN_valid_rsa_csr_and_ec_ca_WHEN_signServerCertificateRequest_THEN_return_valid_certificate()
            throws Exception {
        certificateStore.update(TEST_PASSPHRASE, CAType.ECDSA_P256);

        KeyPair kp = CertificateStore.newRSAKeyPair();
        String csr = CertificateRequestGenerator.createCSR(kp, TEST_CN, null, null);

        X509Certificate certificate = signServerCSRWithCA(csr, Collections.emptyList());
        assertThat(certificate.getSigAlgName(), equalTo(ECDSA_CERT_SIG_ALG));
    }

    @Test
    public void GIVEN_valid_ec_csr_and_rsa_ca_WHEN_signServerCertificateRequest_THEN_return_valid_certificate()
            throws Exception {
        certificateStore.update(TEST_PASSPHRASE, CAType.RSA_2048);

        KeyPair kp = CertificateStore.newECKeyPair();
        String csr = CertificateRequestGenerator.createCSR(kp, TEST_CN, null, null);

        X509Certificate certificate = signServerCSRWithCA(csr, Collections.emptyList());
        assertThat(certificate.getSigAlgName(), equalTo(RSA_CERT_SIG_ALG));
    }

    @Test
    public void GIVEN_invalid_csr_WHEN_signCertificateRequest_THEN_throws() {
        // TODO
    }

    @Test
    public void GIVEN_invalid_ca_WHEN_signCertificateRequest_THEN_throws() {
        // TODO
    }

    @Test
    public void GIVEN_ca_key_does_not_match_ca_cert_WHEN_signCertificateRequest_THEN_throws() {
        // TODO
    }

    @Test
    public void GIVEN_notAfter_before_notBefore_WHEN_signCertificateRequest_THEN_throws() {
        // TODO
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void GIVEN_csr_with_ip_address_WHEN_signServerCertificateRequest_THEN_returns_correct_certificate()
            throws Exception {
        certificateStore.update(TEST_PASSPHRASE, CAType.RSA_2048);

        KeyPair kp = CertificateStore.newRSAKeyPair();
        String csr = CertificateRequestGenerator.createCSR(kp, TEST_CN, null, null);

        X509Certificate certificate = signServerCSRWithCA(csr, Collections.singletonList("172.8.8.10"));
        assertThat(certificate.getSigAlgName(), equalTo(RSA_CERT_SIG_ALG));
        assertThat(certificate.getSubjectAlternativeNames().toString(), containsString("172.8.8.10"));
    }

    @Test
    public void GIVEN_csr_with_dns_name_WHEN_signServerCertificateRequest_THEN_returns_correct_certificate()
            throws Exception {
        certificateStore.update(TEST_PASSPHRASE, CAType.RSA_2048);

        KeyPair kp = CertificateStore.newRSAKeyPair();
        String csr = CertificateRequestGenerator.createCSR(kp, TEST_CN, null, null);

        X509Certificate certificate = signServerCSRWithCA(csr, Collections.singletonList("localhost"));
        assertThat(certificate.getSigAlgName(), equalTo(RSA_CERT_SIG_ALG));
        assertThat(certificate.getSubjectAlternativeNames().toString(), containsString("localhost"));
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void GIVEN_csr_with_dns_and_ip_WHEN_signServerCertificateRequest_THEN_returns_correct_certificate()
            throws Exception {
        certificateStore.update(TEST_PASSPHRASE, CAType.RSA_2048);

        KeyPair kp = CertificateStore.newRSAKeyPair();
        String csr = CertificateRequestGenerator.createCSR(kp, TEST_CN, null, null);

        X509Certificate certificate = signServerCSRWithCA(csr, Arrays.asList("172.8.8.10", "localhost"));
        assertThat(certificate.getSigAlgName(), equalTo(RSA_CERT_SIG_ALG));
        assertThat(certificate.getSubjectAlternativeNames().toString(), containsString("172.8.8.10"));
        assertThat(certificate.getSubjectAlternativeNames().toString(), containsString("localhost"));
    }

    @Test
    public void GIVEN_csr_with_duplicate_dns_WHEN_signServerCertificateRequest_THEN_dns_deduplicated()
            throws Exception {
        certificateStore.update(TEST_PASSPHRASE, CAType.RSA_2048);

        KeyPair kp = CertificateStore.newRSAKeyPair();
        String csr = CertificateRequestGenerator.createCSR(kp, TEST_CN, null, null);

        X509Certificate certificate = signServerCSRWithCA(csr, Arrays.asList("localhost", "localhost"));
        assertThat(certificate.getSigAlgName(), equalTo(RSA_CERT_SIG_ALG));
        assertThat(certificate.getSubjectAlternativeNames().toString(), containsString("localhost"));
        assertThat(certificate.getSubjectAlternativeNames().size(), equalTo(1));
    }

    @Test
    public void GIVEN_valid_csr_and_ca_WHEN_signClientCertificateRequest_THEN_returns_client_certificate()
            throws Exception {
        certificateStore.update(TEST_PASSPHRASE, CAType.RSA_2048);

        KeyPair kp = CertificateStore.newRSAKeyPair();
        String csr = CertificateRequestGenerator.createCSR(kp, TEST_CN, null, null);

        Instant now = Instant.now();
        PKCS10CertificationRequest pkcs10CertificationRequest =
                CertificateHelper.getPKCS10CertificationRequestFromPem(csr);
        JcaPKCS10CertificationRequest jcaRequest = new JcaPKCS10CertificationRequest(pkcs10CertificationRequest);
        X509Certificate certificate = CertificateHelper.signClientCertificateRequest(
                certificateStore.getCACertificate(),
                certificateStore.getCAPrivateKey(),
                jcaRequest.getSubject(),
                jcaRequest.getPublicKey(),
                Date.from(now),
                Date.from(now.plusSeconds(60)));

        assertThat(certificate.getIssuerX500Principal().getName(), equalTo(EXPECTED_ISSUER_PRINCIPAL));
        assertThat(certificate.getSubjectX500Principal().getName(), equalTo(EXPECTED_SUBJECT_PRINCIPAL));
        assertThat(certificate.getSigAlgName(), equalTo(RSA_CERT_SIG_ALG));
        assertThat(certificate.getExtendedKeyUsage().get(0), equalTo(KeyPurposeId.id_kp_clientAuth.getId()));
    }
}
