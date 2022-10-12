/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.infra;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.util.FileSystemPermission;
import com.aws.greengrass.util.platforms.Platform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Optional;
import javax.inject.Inject;

/**
 * A KeyStore for client certificates. When a client device provides their certificate we store it
 * in this store it so that later we can refresh it later on using the cloud API.
 */
public class ClientCertificateStore {
    private static final String STORE_NAME = "client.jks";
    private final Path storePath;
    private final RuntimeConfiguration runtimeConfiguration;
    private KeyStore keyStore;
    private char[] passphrase;


    /**
     * Create a certificate store for tests.
     *
     * @param runtimeConfiguration - Runtime configuration
     * @param kernel - Nucleus Kernel
     *
     * @throws  KeyStoreException - If fails to create or load key store
     * @throws  IOException - If fails to get work path
     */
    @Inject
    public ClientCertificateStore(RuntimeConfiguration runtimeConfiguration, Kernel kernel) throws KeyStoreException,
            IOException {
        this(
            runtimeConfiguration,
            kernel.getNucleusPaths().workPath(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME)
        );
    }


    /**
     * Create a certificate store for tests.
     *
     * @param runtimeConfiguration - Runtime configuration
     * @param storePath - Path to the key store in the file system
     *
     * @throws  KeyStoreException - If fails to create or load key store
     */
    public ClientCertificateStore(RuntimeConfiguration runtimeConfiguration, Path storePath) throws KeyStoreException {
        this.storePath = storePath.resolve(STORE_NAME);
        this.runtimeConfiguration = runtimeConfiguration;

        this.init();
    }

    private void init() throws KeyStoreException {
        String passphrase = runtimeConfiguration.getClientPassphrase();

        if (passphrase == null) {
            passphrase = CertificateHelper.generateRandomPassphrase();
            runtimeConfiguration.updateClientPassphrase(passphrase);
        }

        this.passphrase = passphrase.toCharArray();

        try {
            this.keyStore = loadStore();
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException
                 | UnrecoverableKeyException e) {
            this.keyStore = createStore();
        }
    }

    private KeyStore loadStore() throws KeyStoreException, IOException,
            CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream ksInputStream = Files.newInputStream(storePath)) {
            ks.load(ksInputStream, passphrase);
        }

        return ks;
    }

    private KeyStore createStore() throws KeyStoreException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        try {
            ks.load(null, null);
            flushToDisk(ks);
        } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new KeyStoreException("unable to load client keystore", e);
        }

        return ks;
    }

    /**
     * Writes the KeyStore into the file system.
     * TODO: Do this async we don't have to block.
     */
    private void flushToDisk(KeyStore ks) throws KeyStoreException {
        try {
            Files.createDirectories(storePath.getParent());

            try (OutputStream writeStream = Files.newOutputStream(storePath)) {
                ks.store(writeStream, passphrase);
            }

            Platform platform = Platform.getInstance();
            FileSystemPermission accessLevel = FileSystemPermission.builder()
                    .ownerRead(true).ownerWrite(true).build();

            platform.setPermissions(accessLevel, storePath);
        } catch (CertificateException | IOException | NoSuchAlgorithmException e) {
            throw new KeyStoreException(e);
        }
    }


    /**
     * Stores the PEM for a certificate.
     * @param certificateId - A Certificate ID
     * @param certificatePem - The Pem string of the certificate
     *
     * @throws CertificateException - If fails generate certificate from PEM
     * @throws KeyStoreException - If fails to store the key store into disk
     */
    public void storePem(String certificateId, String certificatePem) throws CertificateException, KeyStoreException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream targetStream = new ByteArrayInputStream(certificatePem.getBytes());
        Certificate cert = cf.generateCertificate(targetStream);
        keyStore.setCertificateEntry(certificateId, cert);
        flushToDisk(keyStore);
    }

    /**
     * Returns the PEM for a certificate.
     *
     * @param certificateId - The id of a Certificate
     */
    public Optional<String> getPem(String certificateId) {
        try {
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(certificateId);

            if (cert == null) {
                return Optional.empty();
            }

            return Optional.of(CertificateHelper.toPem(cert));
        } catch (KeyStoreException | CertificateEncodingException | IOException e) {
            return Optional.empty();
        }
    }
}
