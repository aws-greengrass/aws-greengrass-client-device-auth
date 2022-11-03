/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.certificate.events.CACertificateChainChanged;
import com.aws.greengrass.clientdevices.auth.exception.CertificateChainLoadingException;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.security.exceptions.KeyLoadingException;
import com.aws.greengrass.security.exceptions.ServiceUnavailableException;
import com.aws.greengrass.util.EncryptionUtils;
import com.aws.greengrass.util.FileSystemPermission;
import com.aws.greengrass.util.platforms.Platform;
import lombok.AccessLevel;
import lombok.Getter;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
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
import java.util.Arrays;
import java.util.Date;
import javax.inject.Inject;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;

public class CertificateStore {
    private static final long DEFAULT_CA_EXPIRY_SECONDS = 60 * 60 * 24 * 365 * 5; // 5 years
    private static final String DEFAULT_CA_CN = "Greengrass Core CA";
    private static final String CA_KEY_ALIAS = "CA";
    private static final String DEFAULT_KEYSTORE_FILENAME = "ca.jks";
    private static final String DEFAULT_CA_CERTIFICATE_FILENAME = "ca.pem";

    // Current NIST recommendation is to provide at least 112 bits
    // of security strength through 2030
    // https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-57pt1r5.pdf
    // RSA 2048 is used on v1 and provides 112 bits
    // NIST-P256 (secp256r1) provides 128 bits
    private static final int RSA_KEY_LENGTH = 2048;
    private static final String EC_DEFAULT_CURVE = "secp256r1";
    private static final FileSystemPermission OWNER_RW_ONLY =
            FileSystemPermission.builder().ownerRead(true).ownerWrite(true).build();

    private final Logger logger = LogManager.getLogger(CertificateStore.class);
    private final SecurityService securityService;
    @Getter
    private KeyStore keyStore;
    @Getter(AccessLevel.PRIVATE)
    private char[] passphrase;
    private final Path workPath;
    private final Platform platform = Platform.getInstance();
    private final DomainEvents eventEmitter;

    @Getter
    private X509Certificate[] caCertificateChain;
    private PrivateKey caPrivateKey;
    @Getter
    private CertificateHelper.ProviderType providerType;


    public enum CAType {
        RSA_2048, ECDSA_P256
    }

    @Inject
    public CertificateStore(Kernel kernel, DomainEvents eventEmitter, SecurityService securityService)
            throws IOException {
        this(kernel.getNucleusPaths().workPath(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME), eventEmitter,
                securityService);
    }

    /**
     * Create a certificate store for tests.
     *
     * @param workPath        test path
     * @param eventEmitter    domain events
     * @param securityService security service
     */
    public CertificateStore(Path workPath, DomainEvents eventEmitter, SecurityService securityService) {
        this.workPath = workPath;
        this.eventEmitter = eventEmitter;
        this.securityService = securityService;
    }

    public String getCaPassphrase() {
        return passphrase == null ? null : new String(passphrase);
    }

    /**
     * Initialize CA keystore.
     *
     * @param passphrase Passphrase used for KeyStore and private key entries.
     * @param caType     CA key type.
     * @throws KeyStoreException if unable to load or create CA KeyStore
     */
    public void update(String passphrase, CAType caType) throws KeyStoreException {
        this.passphrase = passphrase.toCharArray();
        try {
            logger.info("Loading Greengrass managed certificate authority");
            keyStore = loadDefaultKeyStore(caType);
            logger.atDebug().log("successfully loaded existing CA keystore");
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException
                 | UnrecoverableKeyException e) {
            logger.atDebug().cause(e).log("Failed to load existing CA keystore, creating Greengrass managed CA");
            createAndStoreDefaultKeyStore(caType);
            logger.atDebug().log("successfully created new CA keystore");
        }

        try {
            setCaKeyAndCertificateChain(keyStore.getKey(CA_KEY_ALIAS, getPassphrase()),
                    keyStore.getCertificateChain(CA_KEY_ALIAS));
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new KeyStoreException("unable to retrieve CA private key", e);
        }
    }

    /**
     * Get CA PrivateKey.
     *
     * @return CA PrivateKey object
     */
    public PrivateKey getCAPrivateKey() {
        return caPrivateKey;
    }

    /**
     * Get certificate chain using private and certificate URIs.
     *
     * @param privateKeyUri  private key URI
     * @param certificateUri certificate URI
     * @return X509Certificate list that corresponds to the given private key and certificate URIs
     * @throws KeyLoadingException              if it fails to load key
     * @throws CertificateChainLoadingException if no certificate chain is found
     * @throws ServiceUnavailableException      if the security service is not available
     */
    public X509Certificate[] loadCaCertificateChain(URI privateKeyUri, URI certificateUri)
            throws CertificateChainLoadingException, KeyLoadingException, ServiceUnavailableException {
        KeyManager[] km = securityService.getKeyManagers(privateKeyUri, certificateUri);

        if (km.length != 1 || !(km[0] instanceof X509KeyManager)) {
            throw new CertificateChainLoadingException("Unable to find the X509 key manager instance to get the "
                    + "certificate chain using the private key and certificate URIs");
        }

        X509KeyManager x509KeyManager = (X509KeyManager) km[0];
        KeyPair keyPair = securityService.getKeyPair(privateKeyUri, certificateUri);
        String[] aliases = x509KeyManager.getClientAliases(keyPair.getPublic().getAlgorithm(), null);
        if (aliases == null) {
            throw new CertificateChainLoadingException(
                    "Unable to find aliases in the key manager with the given private key and certificate URIs");
        }

        // TODO: We are making the assumption that the keyStore built by the security service provider will always
        //  have at most 1 certificate (hence one single client alias). Ideally we don't have to make that assumption
        //  and this logic could all live on the security service.
        String alias = aliases[0];
        X509Certificate[] chain = x509KeyManager.getCertificateChain(alias);

        if (chain == null || chain.length < 1) {
            throw new CertificateChainLoadingException(
                    "Unable to get the certificate chain using the private key and certificate URIs");
        }

        return chain;
    }

    /**
     * Checks if the certificate store is ready, which happens once it has all the required fields to provide what is
     * required to issue certificates.
     */
    public boolean isReady() {
        return this.getCaCertificateChain() != null && this.getCAPrivateKey() != null && this.getProviderType() != null;
    }

    /**
     * Get CA Public Certificate.
     *
     * @return CA X509Certificate object
     * @throws KeyStoreException if unable to retrieve the certificate
     */
    public X509Certificate getCACertificate() throws KeyStoreException {
        X509Certificate[] certChain = getCaCertificateChain();
        if (certChain == null) {
            throw new KeyStoreException("No CA certificate configured");
        }
        return certChain[0];
    }


    /**
     * Sets the CA chain and private key that are used to generate certificates. It combines setting both values at the
     * same time to avoid invalid states where the caChain can be updated without updating the value of the private key
     * required to sign generated certificates.
     *
     * @param privateKey         leaf CA private key
     * @param caCertificateChain a CA chain
     * @param providerType       provider type DEFAULT or HSM, used to map to the correct JCA provider
     * @throws KeyStoreException if privateKey is not instance of PrivateKey or no ca chain provided
     */
    public synchronized void setCaKeyAndCertificateChain(CertificateHelper.ProviderType providerType, Key privateKey,
                                                         X509Certificate... caCertificateChain)
            throws KeyStoreException {
        if (caCertificateChain == null) {
            throw new KeyStoreException("No certificate chain provided");
        }

        if (!(privateKey instanceof PrivateKey)) {
            throw new KeyStoreException("unable to retrieve CA private key");
        }

        this.providerType = providerType;
        this.caCertificateChain = caCertificateChain;
        caPrivateKey = (PrivateKey) privateKey;

        logger.atInfo().kv("subject", caCertificateChain[0].getSubjectX500Principal())
                .log("Configured new certificate authority");
        eventEmitter.emit(new CACertificateChainChanged(caCertificateChain));
    }

    private void setCaKeyAndCertificateChain(Key privateKey, Certificate... caCertificateChain)
            throws KeyStoreException {
        if (caCertificateChain == null) {
            throw new KeyStoreException("No certificate chain provided");
        }

        for (Certificate cert : caCertificateChain) {
            if (!(cert instanceof X509Certificate)) {
                throw new KeyStoreException("Unsupported certificate type");
            }
        }

        X509Certificate[] certificates = Arrays.stream(caCertificateChain).toArray(X509Certificate[]::new);
        setCaKeyAndCertificateChain(CertificateHelper.ProviderType.DEFAULT, privateKey, certificates);
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
            caCertificate = CertificateHelper.createCACertificate(kp, notBefore, notAfter, DEFAULT_CA_CN,
                    CertificateHelper.ProviderType.DEFAULT);
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
        caCertificateChain = new X509Certificate[]{caCertificate};
        ks.setKeyEntry(CA_KEY_ALIAS, kp.getPrivate(), getPassphrase(), caCertificateChain);
        keyStore = ks;

        try {
            saveKeyStore();
        } catch (IOException | CertificateException | NoSuchAlgorithmException ex) {
            throw new KeyStoreException("unable to store CA keystore", ex);
        }
    }

    private KeyPair newKeyPair(CAType caType) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
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
        return newRSAKeyPair(RSA_KEY_LENGTH);
    }

    /**
     * Generates an RSA key pair.
     *
     * @param keySize key size
     * @return RSA KeyPair
     * @throws NoSuchAlgorithmException if unable to generate RSA key
     */
    public static KeyPair newRSAKeyPair(int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(CertificateHelper.KEY_TYPE_RSA);
        kpg.initialize(keySize);
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

    private KeyStore loadDefaultKeyStore(CAType caType)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException,
            UnrecoverableKeyException {
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

    private void saveKeyStore() throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        Path caPath = workPath.resolve(DEFAULT_KEYSTORE_FILENAME);
        Files.createDirectories(caPath.getParent());
        try (OutputStream writeStream = Files.newOutputStream(caPath)) {
            keyStore.store(writeStream, getPassphrase());
        }

        platform.setPermissions(OWNER_RW_ONLY, caPath);

        // Write CA to filesystem in PEM format as well for customers not using cloud discovery
        X509Certificate caCert = getCACertificate();
        saveCertificatePem(workPath.resolve(DEFAULT_CA_CERTIFICATE_FILENAME),
                EncryptionUtils.encodeToPem(CertificateHelper.PEM_BOUNDARY_CERTIFICATE, caCert.getEncoded()));
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
