/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.certificatemanager.DCMService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import lombok.AccessLevel;
import lombok.Getter;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import javax.inject.Inject;

public class CAHelper {
    private static final long   DEFAULT_CA_EXPIRY_SECONDS = 60 * 60 * 24 * 365 * 5; // 5 years
    private static final String DEFAULT_CA_CN = "Greengrass Core CA";
    private static final String CA_KEY_ALIAS = "CA";
    private static final String DEFAULT_KEYSTORE_FILENAME = "ca.jks";
    private static final String DEFAULT_CA_CERTIFICATE_FILENAME = "ca.pem";

    // Current NIST recommendation is to provide at least 112 bits
    // of security strength through 2030
    // https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-57pt1r5.pdf
    // RSA 2048 is used on v1 and provides 112 bits
    // NIST-P256 (secp256r1) provides 128 bits
    private static final String RSA_KEY_INSTANCE = "RSA";
    private static final int    RSA_KEY_LENGTH = 2048;
    private static final String EC_KEY_INSTANCE = "EC";
    private static final String EC_DEFAULT_CURVE = "secp256r1";

    private final Logger logger = LogManager.getLogger(CAHelper.class);
    @Getter
    private KeyStore keyStore;
    @Getter(AccessLevel.PRIVATE)
    private char[] passphrase;
    private final Path workPath;


    @Inject
    public CAHelper(Kernel kernel) {
        this.workPath = kernel.getWorkPath().resolve(DCMService.DCM_SERVICE_NAME);
    }

    // For unit tests
    public CAHelper(Path workPath) {
        this.workPath = workPath;
    }

    /**
     * Initialize CA keystore.
     *
     * @param passphrase Passphrase used for KeyStore and private key entries.
     * @throws KeyStoreException if unable to load or create CA KeyStore
     */
    public void init(String passphrase) throws KeyStoreException {
        this.passphrase = passphrase.toCharArray();
        try {
            keyStore = loadDefaultKeyStore();
            logger.atDebug().log("successfully loaded existing CA keystore");
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
            logger.atDebug().log("failed to load existing CA keystore");
            createAndStoreDefaultKeyStore();
            logger.atDebug().log("successfully created new CA keystore");
        }
    }

    /**
     * Get CA PrivateKey.
     *
     * @return                   CA PrivateKey object
     * @throws KeyStoreException if unable to retrieve PrivateKey object
     */
    public PrivateKey getCAPrivateKey() throws KeyStoreException {
        try {
            return (PrivateKey) keyStore.getKey(CA_KEY_ALIAS, getPassphrase());
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new KeyStoreException("unable to retrieve CA private key", e);
        }
    }

    /**
     * Get CA Public Certificate.
     *
     * @return                   CA X509Certificate object
     * @throws KeyStoreException if unable to retrieve the certificate
     */
    public X509Certificate getCACertificate() throws KeyStoreException {
        return (X509Certificate) keyStore.getCertificate(CA_KEY_ALIAS);
    }

    private void createAndStoreDefaultKeyStore() throws KeyStoreException {
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
        ks.setKeyEntry("CA", kp.getPrivate(), getPassphrase(), certificateChain);
        keyStore = ks;

        try {
            saveKeyStore();
        } catch (IOException | CertificateException | NoSuchAlgorithmException ex) {
            throw new KeyStoreException("unable to store CA keystore", ex);
        }
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

    private KeyStore loadDefaultKeyStore() throws KeyStoreException, IOException, CertificateException,
            NoSuchAlgorithmException {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream ksInputStream = Files.newInputStream(workPath.resolve(DEFAULT_KEYSTORE_FILENAME))) {
            ks.load(ksInputStream, getPassphrase());
        }
        return ks;
    }

    private void saveKeyStore() throws IOException, CertificateException,
            NoSuchAlgorithmException, KeyStoreException {
        Path caPath = workPath.resolve(DEFAULT_KEYSTORE_FILENAME);
        caPath.toFile().getParentFile().mkdirs();
        try (OutputStream writeStream = Files.newOutputStream(caPath)) {
            keyStore.store(writeStream, getPassphrase());
        }

        // TODO: Clean this up
        // Temporarily store public CA since CA information is not yet available in cloud
        X509Certificate caCert = getCACertificate();
        try (OutputStream writeStream = Files.newOutputStream(workPath.resolve(DEFAULT_CA_CERTIFICATE_FILENAME))) {
            writeStream.write(CertificateHelper.toPem(caCert).getBytes());
        }
    }

    /**
     * Generates a secure passphrase suitable for use with KeyStores.
     *
     * @return ASCII passphrase
     */
    public static String generateRandomPassphrase() {
        // Generate cryptographically secure sequence of bytes
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : randomBytes) {
            sb.append(byteToAsciiCharacter(b));
        }
        return sb.toString();
    }

    // Map random byte into ASCII space
    static char byteToAsciiCharacter(byte randomByte) {
        return (char) ((randomByte & 0x7F) % ('~' - ' ') + ' ');
    }
}
