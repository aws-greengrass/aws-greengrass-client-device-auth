package com.aws.iot.evergreen.dcm.certificate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ExtendWith({MockitoExtension.class})
public class CAHelperTest {
    static final String RSA_KEY_ALGORITHM = "RSA";
    static final int    RSA_BIT_LENGTH = 2048;
    static final String EC_KEY_ALGORITHM = "EC";
    static final int    EC_BIT_LENGTH = 256;
    static final String RSA_CERT_SIG_ALG = "SHA256WITHRSA";

    @Test
    public void GIVEN_missing_keystore_WHEN_getCAKeyStore_THEN_new_rsa_ca_created() throws KeyStoreException {
        KeyStore ks = CAHelper.getCAKeyStore();
        PrivateKey pk = CAHelper.getCAPrivateKey(ks);
        X509Certificate cert = CAHelper.getCACertificate(ks);

        assertThat(pk.getAlgorithm(), equalTo(RSA_KEY_ALGORITHM));
        assertThat(cert.getSigAlgName(), equalTo(RSA_CERT_SIG_ALG));
    }

    @Test
    public void GIVEN_KeyHelper_WHEN_newRSAKeyPair_THEN_2048_bit_RSA_key_generated() throws NoSuchAlgorithmException {
        KeyPair rsaKeypair = CAHelper.newRSAKeyPair();
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey)rsaKeypair.getPrivate();

        assertThat(rsaPrivateKey.getAlgorithm(), equalTo(RSA_KEY_ALGORITHM));
        assertThat(rsaPrivateKey.getModulus().bitLength(), equalTo(RSA_BIT_LENGTH));
    }

    @Test
    public void GIVEN_KeyHelper_WHEN_newECKeyPair_THEN_nist_p256_ec_key_generated()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        KeyPair ecKeyPair = CAHelper.newECKeyPair();
        ECPrivateKey ecPrivateKey = (ECPrivateKey)ecKeyPair.getPrivate();

        // TODO: Figure out how to test for the correct curve
        assertThat(ecPrivateKey.getAlgorithm(), equalTo(EC_KEY_ALGORITHM));
    }
}
