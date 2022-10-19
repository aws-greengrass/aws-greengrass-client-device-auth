/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.infra;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Optional;


/**
 * A KeyStore for client certificates. When a client device provides their certificate we store it
 * in this store it so that later we can refresh it later on using the cloud API.
 */
public class ClientCertificateStore {
    private final KeyStore keyStore;
    private static final Logger logger = LogManager.getLogger(ClientCertificateStore.class);



    /**
     * Create a certificate store for tests.
     *
     * @throws  KeyStoreException - If fails to create or load key store
     */
    public ClientCertificateStore() throws KeyStoreException {
        this.keyStore = createStore();
    }

    private KeyStore createStore() throws KeyStoreException {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        try {
            ks.load(null, null);
        } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new KeyStoreException("unable to load client keystore", e);
        }

        return ks;
    }

    /**
     * Checks if there is a certificate PEM already stored for a certificateId.
     * @param certificateId - The id of the stored certificate.
     */
    public boolean exists(String certificateId) {
        try {
            return keyStore.isCertificateEntry(certificateId);
        } catch (KeyStoreException e) {
            return false;
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
    }

    /**
     * Removes the PEM for a certificateId alias.
     * @param certificateId - a certificate id
     */
    public void removePem(String certificateId) {
        try {
            keyStore.deleteEntry(certificateId);
        } catch (KeyStoreException e) {
            logger.atError().cause(e).kv("certificateId", certificateId).log("Failed to remove certificate PEM");
        }
    }

    /**
     * Returns the PEM for a certificate.
     *
     * @param certificateId - The id of a Certificate
     */
    public Optional<String> getPem(String certificateId) {
        if (certificateId == null) {
            return Optional.empty();
        }

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
