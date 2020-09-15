/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.certificatemanager.certificate;

import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;

public final class CAHelper {
    private static final long   DEFAULT_CA_EXPIRY_SECONDS = 60 * 60 * 24 * 365 * 5; // 5 years
    private static final String DEFAULT_KEYSTORE_PASSWORD = "";
    private static final String DEFAULT_CA_CN = "Greengrass Core CA";
    private static final String CA_KEY_ALIAS = "CA";

    // Current NIST recommendation is to provide at least 112 bits
    // of security strength through 2030
    // https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-57pt1r5.pdf
    // RSA 2048 is used on v1 and provides 112 bits
    // NIST-P256 (secp256r1) provides 128 bits
    private static final String RSA_KEY_INSTANCE = "RSA";
    private static final int    RSA_KEY_LENGTH = 2048;
    private static final String EC_KEY_INSTANCE = "EC";
    private static final String EC_DEFAULT_CURVE = "secp256r1";

    private CAHelper(){
    }

    /**
     * Retrieve CA keystore.
     *
     * @return CA KeyStore object
     * @throws KeyStoreException if unable to load or create CA KeyStore
     */
    public static KeyStore getCAKeyStore() throws KeyStoreException {
        KeyStore ks;
        try {
            ks = loadCAKeyStore();
        } catch (KeyStoreException e) {
            ks = createDefaultCAKeyStore();
        }

        return ks;
    }

    /**
     * Get CA PrivateKey.
     *
     * @param caKeyStore         KeyStore containing the CA
     * @return                   CA PrivateKey object
     * @throws KeyStoreException if unable to retrieve PrivateKey object
     */
    public static PrivateKey getCAPrivateKey(KeyStore caKeyStore) throws KeyStoreException {
        try {
            return (PrivateKey)caKeyStore.getKey(CA_KEY_ALIAS, DEFAULT_KEYSTORE_PASSWORD.toCharArray());
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new KeyStoreException("unable to retrieve CA private key", e);
        }
    }

    /**
     * Get CA Public Certificate.
     *
     * @param caKeyStore         KeyStore containing the CA
     * @return                   CA X509Certificate object
     * @throws KeyStoreException if unable to retrieve the certificate
     */
    public static X509Certificate getCACertificate(KeyStore caKeyStore) throws KeyStoreException {
        return (X509Certificate)caKeyStore.getCertificate(CA_KEY_ALIAS);
    }

    private static KeyStore createDefaultCAKeyStore() throws KeyStoreException {
        KeyPair kp;

        // Generate CA keypair
        try {
            kp = newRSAKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new KeyStoreException("unable to generate keypair for CA key store", e);
        }

        // Create CA certificate
        X509Certificate caCertificate;
        try {
            Instant now = Instant.now();
            Date notBefore = Date.from(now);
            Date notAfter = Date.from(now.plusSeconds(DEFAULT_CA_EXPIRY_SECONDS));
            caCertificate = CertificateHelper.createCACertificate(
                    kp, notBefore, notAfter, DEFAULT_CA_CN);
        } catch (NoSuchAlgorithmException | CertIOException | OperatorCreationException | CertificateException e) {
            throw new KeyStoreException("unable to generate CA certificate", e);
        }

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try {
            ks.load(null, null);
        } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new KeyStoreException("unable to load CA keystore", e);
        }
        Certificate[] certificateChain = { caCertificate };
        ks.setKeyEntry("CA", kp.getPrivate(), DEFAULT_KEYSTORE_PASSWORD.toCharArray(), certificateChain);

        return ks;
    }

    /**
     * Generates an RSA key pair.
     *
     * @return RSA KeyPair
     * @throws NoSuchAlgorithmException if unable to generate RSA key
     */
    public static KeyPair newRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(RSA_KEY_INSTANCE);
        kpg.initialize(RSA_KEY_LENGTH);
        return kpg.generateKeyPair();
    }

    /**
     * Generates an EC key pair.
     *
     * @return EC KeyPair
     * @throws NoSuchAlgorithmException           if unable to generate EC key
     * @throws InvalidAlgorithmParameterException if unable to initialize with given EC curve
     */
    public static KeyPair newECKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(EC_KEY_INSTANCE);
        kpg.initialize(new ECGenParameterSpec(EC_DEFAULT_CURVE));
        return kpg.generateKeyPair();
    }

    private static KeyStore loadCAKeyStore() throws KeyStoreException {
        // TODO
        throw new KeyStoreException("Not found");
    }
}
