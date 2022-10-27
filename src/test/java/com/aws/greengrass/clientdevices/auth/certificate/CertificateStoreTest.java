/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore.CAType;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
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
import static org.hamcrest.Matchers.not;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CertificateStoreTest {
    static final String RSA_KEY_ALGORITHM = "RSA";
    static final int    RSA_BIT_LENGTH = 2048;
    static final String EC_KEY_ALGORITHM = "EC";
    static final int    EC_BIT_LENGTH = 256;
    static final String RSA_CERT_SIG_ALG = "SHA256withRSA";
    static final String ECDSA_CERT_SIG_ALG = "SHA256withECDSA";
    static final String DEFAULT_PASSPHRASE = "defaultPassphrase";

    private CertificateStore certificateStore;

    @TempDir
    Path tmpPath;
    @Mock
    SecurityService securityService;


    @BeforeEach
    public void beforeEach() {
        certificateStore = new CertificateStore(tmpPath, new DomainEvents(), securityService);
    }

    @AfterEach
    public void afterEach() throws IOException {
        FileUtils.cleanDirectory(tmpPath.toFile());
    }

    @Test
    public void GIVEN_missing_keystore_WHEN_init_with_RSA_THEN_new_rsa_ca_created() throws KeyStoreException {
        certificateStore.update(DEFAULT_PASSPHRASE, CAType.RSA_2048);
        PrivateKey pk = certificateStore.getCAPrivateKey();
        X509Certificate cert = certificateStore.getCACertificate();

        assertThat(pk.getAlgorithm(), equalTo(RSA_KEY_ALGORITHM));
        assertThat(cert.getSigAlgName(), equalTo(RSA_CERT_SIG_ALG));
        assertThat(certificateStore.getCaPassphrase(), not(DEFAULT_PASSPHRASE));
    }

    @Test
    public void GIVEN_missing_keystore_WHEN_init_with_EC_THEN_new_ec_ca_created() throws KeyStoreException {
        certificateStore.update(DEFAULT_PASSPHRASE, CAType.ECDSA_P256);
        PrivateKey pk = certificateStore.getCAPrivateKey();
        X509Certificate cert = certificateStore.getCACertificate();

        assertThat(pk.getAlgorithm(), equalTo(EC_KEY_ALGORITHM));
        assertThat(cert.getSigAlgName(), equalTo(ECDSA_CERT_SIG_ALG));
    }

    @Test
    public void GIVEN_CertificateStore_WHEN_newRSAKeyPair_THEN_2048_bit_RSA_key_generated()
            throws NoSuchAlgorithmException {
        KeyPair rsaKeypair = certificateStore.newRSAKeyPair();
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey)rsaKeypair.getPrivate();

        assertThat(rsaPrivateKey.getAlgorithm(), equalTo(RSA_KEY_ALGORITHM));
        assertThat(rsaPrivateKey.getModulus().bitLength(), equalTo(RSA_BIT_LENGTH));
    }

    @Test
    public void GIVEN_CertificateStore_WHEN_newECKeyPair_THEN_nist_p256_ec_key_generated()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        KeyPair ecKeyPair = certificateStore.newECKeyPair();
        ECPrivateKey ecPrivateKey = (ECPrivateKey)ecKeyPair.getPrivate();

        assertThat(ecPrivateKey.getAlgorithm(), equalTo(EC_KEY_ALGORITHM));
        assertThat(ecPrivateKey.getParams().getCurve().getField().getFieldSize(), equalTo(EC_BIT_LENGTH));
    }

    @Test
    public void GIVEN_existing_keystore_WHEN_initialized_with_passphrase_and_same_ca_type_THEN_keystore_is_loaded()
            throws KeyStoreException {
        certificateStore.update(DEFAULT_PASSPHRASE, CAType.RSA_2048);
        X509Certificate initialCert = certificateStore.getCACertificate();
        String passphrase = certificateStore.getCaPassphrase();

        CertificateStore certificateStore2 = new CertificateStore(tmpPath, new DomainEvents(), securityService);
        certificateStore2.update(passphrase, CAType.RSA_2048);
        X509Certificate secondCert = certificateStore2.getCACertificate();

        assertThat(initialCert, equalTo(secondCert));
    }

    @Test
    public void GIVEN_existing_keystore_WHEN_initialized_with_wrong_passphrase_THEN_new_keystore_is_created()
            throws KeyStoreException {
        certificateStore.update(DEFAULT_PASSPHRASE, CAType.RSA_2048);
        X509Certificate initialCert = certificateStore.getCACertificate();

        CertificateStore certificateStore2 = new CertificateStore(tmpPath, new DomainEvents(), securityService);
        certificateStore2.update("wrongPassphrase", CAType.RSA_2048);
        X509Certificate secondCert = certificateStore2.getCACertificate();

        assertThat(initialCert, not(equalTo(secondCert)));
    }

    @Test
    public void GIVEN_existing_keystore_WHEN_initialized_with_different_ca_type_THEN_new_keystore_is_created()
            throws KeyStoreException {
        certificateStore.update(DEFAULT_PASSPHRASE, CAType.RSA_2048);
        X509Certificate initialCert = certificateStore.getCACertificate();

        CertificateStore certificateStore2 = new CertificateStore(tmpPath, new DomainEvents(), securityService);
        certificateStore2.update(DEFAULT_PASSPHRASE, CAType.ECDSA_P256);
        X509Certificate secondCert = certificateStore2.getCACertificate();

        assertThat(initialCert, not(equalTo(secondCert)));
    }

    @Test
    public void GIVEN_CertificateStore_WHEN_generateRandomPassphrase_THEN_ascii_passphrase_consistently_returned() {
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
}
