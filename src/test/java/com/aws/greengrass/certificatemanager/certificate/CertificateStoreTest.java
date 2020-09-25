/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

@ExtendWith({MockitoExtension.class})
public class CertificateStoreTest {
    static final String RSA_KEY_ALGORITHM = "RSA";
    static final int    RSA_BIT_LENGTH = 2048;
    static final String EC_KEY_ALGORITHM = "EC";
    static final int    EC_BIT_LENGTH = 256;
    static final String RSA_CERT_SIG_ALG = "SHA256WITHRSA";
    static final String DEFAULT_PASSPHRASE = "defaultPassphrase";

    private CertificateStore certificateStore;

    @TempDir
    Path tmpPath;

    @BeforeEach
    public void beforeEach() throws KeyStoreException {
        certificateStore = new CertificateStore(tmpPath);
        certificateStore.init(DEFAULT_PASSPHRASE);
    }

    @AfterEach
    public void afterEach() throws IOException {
        FileUtils.cleanDirectory(tmpPath.toFile());
    }

    @Test
    public void GIVEN_missing_keystore_WHEN_getCAKeyStore_THEN_new_rsa_ca_created() throws KeyStoreException {
        PrivateKey pk = certificateStore.getCAPrivateKey();
        X509Certificate cert = certificateStore.getCACertificate();

        assertThat(pk.getAlgorithm(), equalTo(RSA_KEY_ALGORITHM));
        assertThat(cert.getSigAlgName(), equalTo(RSA_CERT_SIG_ALG));
    }

    @Test
    public void GIVEN_KeyHelper_WHEN_newRSAKeyPair_THEN_2048_bit_RSA_key_generated() throws NoSuchAlgorithmException {
        KeyPair rsaKeypair = certificateStore.newRSAKeyPair();
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey)rsaKeypair.getPrivate();

        assertThat(rsaPrivateKey.getAlgorithm(), equalTo(RSA_KEY_ALGORITHM));
        assertThat(rsaPrivateKey.getModulus().bitLength(), equalTo(RSA_BIT_LENGTH));
    }

    @Test
    public void GIVEN_KeyHelper_WHEN_newECKeyPair_THEN_nist_p256_ec_key_generated()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        KeyPair ecKeyPair = certificateStore.newECKeyPair();
        ECPrivateKey ecPrivateKey = (ECPrivateKey)ecKeyPair.getPrivate();

        // TODO: Figure out how to test for the correct curve
        assertThat(ecPrivateKey.getAlgorithm(), equalTo(EC_KEY_ALGORITHM));
    }

    @Test
    public void GIVEN_existing_keystore_WHEN_getCAKeyStore_called_with_passphrase_THEN_keystore_is_loaded()
            throws KeyStoreException {
        X509Certificate initialCert = certificateStore.getCACertificate();

        CertificateStore helper2 = new CertificateStore(tmpPath);
        helper2.init(DEFAULT_PASSPHRASE);
        X509Certificate secondCert = helper2.getCACertificate();

        Assertions.assertTrue(initialCert.equals(secondCert));
    }

    @Test
    public void GIVEN_existing_keystore_WHEN_getCAKeyStore_called_with_wrong_passphrase_THEN_new_keystore_is_created()
            throws KeyStoreException {
        X509Certificate initialCert = certificateStore.getCACertificate();

        CertificateStore helper2 = new CertificateStore(tmpPath);
        helper2.init("wrongPassphrase");
        X509Certificate secondCert = helper2.getCACertificate();

        Assertions.assertTrue(!initialCert.equals(secondCert));
    }

    @Test
    public void GIVEN_CAHelper_WHEN_generateRandomPassphrase_THEN_ascii_passphrase_consistently_returned() {
        for (int x = 0; x < 1000; x++) {
            String passphrase = CertificateStore.generateRandomPassphrase();
            assertThat(passphrase.length(), equalTo(16));
            for (int i = 0; i < passphrase.length(); i++) {
                assertThat(passphrase.charAt(i), greaterThanOrEqualTo(' '));
                assertThat(passphrase.charAt(i), lessThanOrEqualTo('~'));
            }
        }
    }

    @Test
    public void GIVEN_all_possible_byte_values_WHEN_byteToAsciiCharacter_THEN_values_are_converted_to_ascii_char() {
        for (byte c = -128; c < 127; c++) {
            assertThat(CertificateStore.byteToAsciiCharacter(c), greaterThanOrEqualTo(' '));
            assertThat(CertificateStore.byteToAsciiCharacter(c), lessThanOrEqualTo('~'));
        }
    }

    @Test
    public void Test_certificateIdToPath_returns_path_inside_work_path() {
        Path certPath = certificateStore.certificateIdToPath("certId");
        Assertions.assertTrue(certPath.toAbsolutePath().startsWith(tmpPath.toAbsolutePath()));
    }

    @Test
    public void Test_certificateIdToPath_returns_unique_path_for_two_certificate_ids() {
        Path certPath1 = certificateStore.certificateIdToPath("certId1");
        Path certPath2 = certificateStore.certificateIdToPath("certId2");
        Assertions.assertNotEquals(certPath1, certPath2);
    }

    @Test
    public void Test_storeDeviceCertificateIfNotPresent_writes_certificate_to_work_directory() throws IOException {
        String certId = "certId";
        String expectedCert = "key_contents";
        certificateStore.storeDeviceCertificateIfNotPresent(certId, expectedCert);

        String actualCert = new String(Files.readAllBytes(certificateStore.certificateIdToPath(certId)));
        Assertions.assertEquals(expectedCert, actualCert);
    }

    @Test
    public void Test_storeDeviceCertificateIfNotPresent_does_not_overwrite_existing_certificate() throws IOException {
        String certId = "certId";
        String expectedCert = "key_contents";
        String secondCert = "should_not_be_written";
        certificateStore.storeDeviceCertificateIfNotPresent(certId, expectedCert);
        certificateStore.storeDeviceCertificateIfNotPresent(certId, secondCert);

        String actualCert = new String(Files.readAllBytes(certificateStore.certificateIdToPath(certId)));
        Assertions.assertEquals(expectedCert, actualCert);
    }

    @Test
    public void Test_loadDeviceCertificate_throws_if_no_certificate_present() {
        String certId = "certId";
        Assertions.assertThrows(IOException.class, () -> certificateStore.loadDeviceCertificate(certId));
    }

    @Test
    public void Test_loadDeviceCertificate_loads_device_certificate_if_present() throws IOException {
        String certId = "certId";
        String expectedContents = "certContents";

        Path certPath = certificateStore.certificateIdToPath(certId);
        Files.createDirectories(certPath.getParent());
        Files.write(certPath, expectedContents.getBytes());

        String actualContents = certificateStore.loadDeviceCertificate(certId);
        Assertions.assertEquals(expectedContents, actualContents);
    }
}
