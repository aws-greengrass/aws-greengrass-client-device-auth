/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.device.ClientDevicesAuthService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.FileSystemPermission;
import com.aws.greengrass.util.platforms.Platform;
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
import java.security.Key;
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
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import javax.inject.Inject;

public class CertificateStore {
    private static final long   DEFAULT_CA_EXPIRY_SECONDS = 60 * 60 * 24 * 365 * 5; // 5 years
    private static final String DEFAULT_CA_CN = "Greengrass Core CA";
    private static final String CA_KEY_ALIAS = "CA";
    private static final String DEVICE_CERTIFICATE_DIR = "devices";
    private static final String DEFAULT_KEYSTORE_FILENAME = "ca.jks";
    private static final String DEFAULT_CA_CERTIFICATE_FILENAME = "ca.pem";

    // Current NIST recommendation is to provide at least 112 bits
    // of security strength through 2030
    // https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-57pt1r5.pdf
    // RSA 2048 is used on v1 and provides 112 bits
    // NIST-P256 (secp256r1) provides 128 bits
    private static final int    RSA_KEY_LENGTH = 2048;
    private static final String EC_DEFAULT_CURVE = "secp256r1";
    private static final FileSystemPermission OWNER_RW_ONLY =  FileSystemPermission.builder()
            .ownerRead(true).ownerWrite(true).build();

    private final Logger logger = LogManager.getLogger(CertificateStore.class);
    @Getter
    private KeyStore keyStore;
    @Getter(AccessLevel.PRIVATE)
    private char[] passphrase;
    private final Path workPath;
    private final Platform platform = Platform.getInstance();

    public enum CAType {
        RSA_2048, ECDSA_P256
    }

    @Inject
    public CertificateStore(Kernel kernel) throws IOException {
        this.workPath = kernel.getNucleusPaths().workPath(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME);
    }

    // For unit tests
    public CertificateStore(Path workPath) {
        this.workPath = workPath;
    }

    public String getCaPassphrase() {
        return passphrase == null ? null : new String(passphrase);
    }

    /**
     * Initialize CA keystore.
     *
     * @param passphrase   Passphrase used for KeyStore and private key entries.
     * @param caType CA key type.
     * @throws KeyStoreException if unable to load or create CA KeyStore
     */
    public void update(String passphrase, CAType caType) throws KeyStoreException {
        this.passphrase = passphrase.toCharArray();
        try {
            keyStore = loadDefaultKeyStore(caType);
            logger.atDebug().log("successfully loaded existing CA keystore");
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException
                | UnrecoverableKeyException e) {
            logger.atDebug().cause(e).log("failed to load existing CA keystore");
            createAndStoreDefaultKeyStore(caType);
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

    public String loadDeviceCertificate(String certificateId) throws IOException {
        return loadCertificatePem(certificateIdToPath(certificateId));
    }

    /**
     * Store device certificate if not present.
     *
     * @param certificateId  Certificate ID
     * @param certificatePem Certificate PEM
     * @throws IOException   if unable to write certificate to disk
     */
    public void storeDeviceCertificateIfNotPresent(String certificateId, String certificatePem) throws IOException {
        Path filePath = certificateIdToPath(certificateId);
        if (!Files.exists(filePath)) {
            saveCertificatePem(certificateIdToPath(certificateId), certificatePem);
        }
    }

    Path certificateIdToPath(String certificateId) {
        return workPath.resolve(DEVICE_CERTIFICATE_DIR).resolve(certificateId + ".pem");
    }

    private void createAndStoreDefaultKeyStore(CAType caType) throws KeyStoreException {
        KeyPair kp;

        // Generate CA keypair
        try {
            kp = newKeyPair(caType);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
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
        // generate new passphrase for new CA certificate
        passphrase = generateRandomPassphrase().toCharArray();
        Certificate[] certificateChain = { caCertificate };
        ks.setKeyEntry("CA", kp.getPrivate(), getPassphrase(), certificateChain);
        keyStore = ks;

        try {
            saveKeyStore();
        } catch (IOException | CertificateException | NoSuchAlgorithmException ex) {
            throw new KeyStoreException("unable to store CA keystore", ex);
        }
    }

    private KeyPair newKeyPair(CAType caType)
            throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        if (caType.equals(CAType.ECDSA_P256)) {
            return newECKeyPair();
        } else if (caType.equals(CAType.RSA_2048)) {
            return newRSAKeyPair();
        }
        throw new NoSuchAlgorithmException(String.format("Algorithm %s not supported", caType.toString()));
    }

    /**
     * Generates an RSA key pair.
     *
     * @return RSA KeyPair
     * @throws NoSuchAlgorithmException if unable to generate RSA key
     */
    public static KeyPair newRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(CertificateHelper.KEY_TYPE_RSA);
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
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(CertificateHelper.KEY_TYPE_EC);
        kpg.initialize(new ECGenParameterSpec(EC_DEFAULT_CURVE));
        return kpg.generateKeyPair();
    }

    private KeyStore loadDefaultKeyStore(CAType caType) throws KeyStoreException, IOException,
            CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream ksInputStream = Files.newInputStream(workPath.resolve(DEFAULT_KEYSTORE_FILENAME))) {
            ks.load(ksInputStream, getPassphrase());
        }

        Key caKey = ks.getKey(CA_KEY_ALIAS, getPassphrase());
        if (!isKeyOfType(caKey, caType)) {
            throw new KeyStoreException(String.format("Key store with %s CA does not exist", caType.toString()));
        }

        return ks;
    }

    private boolean isKeyOfType(Key key, CAType caType) {
        String algorithm = key.getAlgorithm();
        if (caType.equals(CAType.RSA_2048) && algorithm.equals(CertificateHelper.KEY_TYPE_RSA)) {
            int bitLength = ((RSAPrivateKey) key).getModulus().bitLength();
            return bitLength == 2048;
        } else if (caType.equals(CAType.ECDSA_P256) && algorithm.equals(CertificateHelper.KEY_TYPE_EC)) {
            int fieldSize = ((ECPrivateKey) key).getParams().getCurve().getField().getFieldSize();
            return fieldSize == 256;
        }
        return false;
    }

    private void saveKeyStore() throws IOException, CertificateException,
            NoSuchAlgorithmException, KeyStoreException {
        Path caPath = workPath.resolve(DEFAULT_KEYSTORE_FILENAME);
        Files.createDirectories(caPath.getParent());
        try (OutputStream writeStream = Files.newOutputStream(caPath)) {
            keyStore.store(writeStream, getPassphrase());
        }

        platform.setPermissions(OWNER_RW_ONLY, caPath);

        // TODO: Clean this up
        // Temporarily store public CA since CA information is not yet available in cloud
        X509Certificate caCert = getCACertificate();
        saveCertificatePem(workPath.resolve(DEFAULT_CA_CERTIFICATE_FILENAME), CertificateHelper.toPem(caCert));
    }

    private String loadCertificatePem(Path filePath) throws IOException {
        return new String(Files.readAllBytes(filePath));
    }

    private void saveCertificatePem(Path filePath, String certificatePem) throws IOException {
        Files.createDirectories(filePath.getParent());
        try (OutputStream writeStream = Files.newOutputStream(filePath)) {
            writeStream.write(certificatePem.getBytes());
        }
    }

    /**
     * Generates a secure passphrase suitable for use with KeyStores.
     *
     * @return ASCII passphrase
     */
    static String generateRandomPassphrase() {
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
