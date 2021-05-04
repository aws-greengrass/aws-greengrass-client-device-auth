/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager;

import com.aws.greengrass.certificatemanager.certificate.CertificateGenerator;
import com.aws.greengrass.certificatemanager.certificate.CertificateHelper;
import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.certificatemanager.certificate.ClientCertificateGenerator;
import com.aws.greengrass.certificatemanager.certificate.CsrProcessingException;
import com.aws.greengrass.certificatemanager.certificate.ServerCertificateGenerator;
import com.aws.greengrass.dcmclient.Client;
import com.aws.greengrass.dcmclient.ClientException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import lombok.NonNull;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;

public class CertificateManager {
    private final Logger logger = LogManager.getLogger(CertificateManager.class);

    private final CertificateStore certificateStore;

    private final Client cisClient;

    /**
     * Constructor.
     *
     * @param certificateStore      Helper class for managing certificate authorities
     * @param cisClient             CIS Client
     */
    @Inject
    public CertificateManager(CertificateStore certificateStore, Client cisClient) {
        this.certificateStore = certificateStore;
        this.cisClient = cisClient;
    }

    /**
     * Initialize the certificate manager.
     * @param caPassphrase  CA Passphrase
     * @param caType        CA type
     * @throws KeyStoreException if unable to load the CA key store
     */
    public void update(String caPassphrase, CertificateStore.CAType caType) throws KeyStoreException {
        certificateStore.update(caPassphrase, caType);
    }

    /**
     * Return a list of CA certificates used to issue client certs.
     *
     * @return a list of CA certificates for issuing client certs
     * @throws KeyStoreException if unable to retrieve the certificate
     * @throws IOException if unable to write certificate
     * @throws CertificateEncodingException if unable to get certificate encoding
     */
    public List<String> getCACertificates() throws KeyStoreException, IOException, CertificateEncodingException {
        List<String> caList = new ArrayList<>();
        String caPem = CertificateHelper.toPem(certificateStore.getCACertificate());
        caList.add(caPem);

        return caList;
    }

    /**
     * Subscribe to server certificate updates.
     * <p>
     * The certificate manager will save the given CSR and generate a new certificate under the following scenarios:
     *   1) The previous certificate is nearing expiry
     *   2) GGC connectivity information changes
     * Certificates will continue to be generated until the client calls unsubscribeFromCertificateUpdates.
     * </p>
     *
     * @param csr Certificate signing request
     * @param cb  Certificate consumer
     * @throws KeyStoreException if unable to access KeyStore
     * @throws CsrProcessingException if unable to process CSR
     * @throws ClientException if unable to get connectivity info
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void subscribeToServerCertificateUpdates(@NonNull String csr, @NonNull Consumer<X509Certificate> cb)
            throws KeyStoreException, CsrProcessingException, ClientException {
        // BouncyCastle can throw RuntimeExceptions, and unfortunately it is not easy to detect
        // bad input beforehand. For now, just catch and re-throw a CsrProcessingException
        try {
            PKCS10CertificationRequest pkcs10CertificationRequest =
                    CertificateHelper.getPKCS10CertificationRequestFromPem(csr);
            JcaPKCS10CertificationRequest jcaRequest = new JcaPKCS10CertificationRequest(pkcs10CertificationRequest);
            CertificateGenerator certificateGenerator = new ServerCertificateGenerator(
                    jcaRequest.getSubject(), jcaRequest.getPublicKey(), cb, certificateStore);
            certificateGenerator.generateCertificate(cisClient.getConnectivityInfo());
        } catch (KeyStoreException | ClientException e) {
            logger.atError().setCause(e).log("unable to subscribe to certificate update");
            throw e;
        } catch (RuntimeException | OperatorCreationException | NoSuchAlgorithmException | CertificateException
                | InvalidKeyException | IOException e) {
            throw new CsrProcessingException(csr, e);
        }
    }

    /**
     * Subscribe to client certificate updates.
     * <p>
     * The certificate manager will save the given CSR and generate a new certificate under the following scenarios:
     *   1) The previous certificate is nearing expiry
     * Certificates will continue to be generated until the client calls unsubscribeFromCertificateUpdates.
     * </p>
     *
     * @param csr Certificate signing request
     * @param cb  Certificate consumer
     * @throws KeyStoreException if unable to access KeyStore
     * @throws CsrProcessingException if unable to process CSR
     * @throws ClientException if unable to get connectivity info
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void subscribeToClientCertificateUpdates(@NonNull String csr, @NonNull Consumer<X509Certificate[]> cb)
            throws KeyStoreException, CsrProcessingException, ClientException {
        // BouncyCastle can throw RuntimeExceptions, and unfortunately it is not easy to detect
        // bad input beforehand. For now, just catch and re-throw a CsrProcessingException
        try {
            PKCS10CertificationRequest pkcs10CertificationRequest =
                    CertificateHelper.getPKCS10CertificationRequestFromPem(csr);
            JcaPKCS10CertificationRequest jcaRequest = new JcaPKCS10CertificationRequest(pkcs10CertificationRequest);
            CertificateGenerator certificateGenerator = new ClientCertificateGenerator(
                    jcaRequest.getSubject(), jcaRequest.getPublicKey(), cb, certificateStore);
            certificateGenerator.generateCertificate();
        } catch (KeyStoreException | ClientException e) {
            logger.atError().setCause(e).log("unable to subscribe to certificate update");
            throw e;
        } catch (RuntimeException | OperatorCreationException | NoSuchAlgorithmException | CertificateException
                | InvalidKeyException | IOException e) {
            throw new CsrProcessingException(csr, e);
        }
    }

    /**
     * Unsubscribe from server certificate updates.
     *
     * @param cb Certificate consumer
     */
    public void unsubscribeFromCertificateUpdates(@NonNull Consumer<String> cb) {
        // TODO
    }
}
