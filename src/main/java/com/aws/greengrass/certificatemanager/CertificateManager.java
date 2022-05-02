/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager;

import com.aws.greengrass.certificatemanager.certificate.CISShadowMonitor;
import com.aws.greengrass.certificatemanager.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.certificatemanager.certificate.CertificateGenerator;
import com.aws.greengrass.certificatemanager.certificate.CertificateHelper;
import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.certificatemanager.certificate.CertificatesConfig;
import com.aws.greengrass.certificatemanager.certificate.ClientCertificateGenerator;
import com.aws.greengrass.certificatemanager.certificate.CsrProcessingException;
import com.aws.greengrass.certificatemanager.certificate.ServerCertificateGenerator;
import com.aws.greengrass.cisclient.ConnectivityInfoProvider;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import lombok.NonNull;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.inject.Inject;

public class CertificateManager {
    private final Logger logger = LogManager.getLogger(CertificateManager.class);

    private final CertificateStore certificateStore;

    private final ConnectivityInfoProvider connectivityInfoProvider;

    private final CertificateExpiryMonitor certExpiryMonitor;

    private final CISShadowMonitor cisShadowMonitor;

    private final Map<Consumer<X509Certificate>, CertificateGenerator> serverCertSubscriptions =
            new ConcurrentHashMap<>();

    private CertificatesConfig certificatesConfig;

    /**
     * Constructor.
     *
     * @param certificateStore      Helper class for managing certificate authorities
     * @param connectivityInfoProvider             Connectivity Info Provider
     * @param certExpiryMonitor     Certificate Expiry Monitor
     * @param cisShadowMonitor      CIS Shadow Monitor
     */
    @Inject
    public CertificateManager(CertificateStore certificateStore, ConnectivityInfoProvider connectivityInfoProvider,
                              CertificateExpiryMonitor certExpiryMonitor, CISShadowMonitor cisShadowMonitor) {
        this.certificateStore = certificateStore;
        this.connectivityInfoProvider = connectivityInfoProvider;
        this.certExpiryMonitor = certExpiryMonitor;
        this.cisShadowMonitor = cisShadowMonitor;
    }

    public void updateCertificatesConfiguration(CertificatesConfig certificatesConfig) {
        this.certificatesConfig = certificatesConfig;
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
     * Start certificate monitors.
     */
    public void startMonitors() {
        certExpiryMonitor.startMonitor();
        cisShadowMonitor.startMonitor();
    }

    /**
     * Stop certificate monitors.
     */
    public void stopMonitors() {
        certExpiryMonitor.stopMonitor();
        cisShadowMonitor.stopMonitor();
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
        return Collections.singletonList(CertificateHelper.toPem(certificateStore.getCACertificate()));
    }

    public String getCaPassPhrase() {
        return certificateStore.getCaPassphrase();
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
     * @throws RuntimeException for any unknown runtime error
     */
    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.ExceptionAsFlowControl"})
    public void subscribeToServerCertificateUpdates(@NonNull String csr, @NonNull Consumer<X509Certificate> cb)
            throws KeyStoreException, CsrProcessingException {
        // deduplicate subscriptions with the same callback
        try {
            serverCertSubscriptions.compute(cb, (k, v) -> {
                // A subscription already exists, we will replace it so that a new certificate is generated immediately
                if (v != null) {
                    removeCGFromMonitors(v);
                }

                // BouncyCastle can throw RuntimeExceptions, and unfortunately it is not easy to detect
                // bad input beforehand. For now, just catch and re-throw a CsrProcessingException
                try {
                    PKCS10CertificationRequest pkcs10CertificationRequest =
                            CertificateHelper.getPKCS10CertificationRequestFromPem(csr);
                    JcaPKCS10CertificationRequest jcaRequest =
                            new JcaPKCS10CertificationRequest(pkcs10CertificationRequest);
                    CertificateGenerator certificateGenerator =
                            new ServerCertificateGenerator(jcaRequest.getSubject(), jcaRequest.getPublicKey(), cb,
                                    certificateStore, certificatesConfig);

                    // Add certificate generator to monitors first in order to avoid missing events
                    // that happen while the initial certificate is being generated.
                    certExpiryMonitor.addToMonitor(certificateGenerator);
                    cisShadowMonitor.addToMonitor(certificateGenerator);

                    certificateGenerator.generateCertificate(connectivityInfoProvider::getCachedHostAddresses,
                            "initialization of server cert subscription");
                    return certificateGenerator;
                } catch (KeyStoreException e) {
                    logger.atError().setCause(e).log("unable to subscribe to certificate update");
                    throw new RuntimeException(e);
                } catch (RuntimeException | NoSuchAlgorithmException | InvalidKeyException | IOException e) {
                    throw new RuntimeException(new CsrProcessingException(csr, e));
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() != null) {
                Throwable cause = e.getCause();
                if (cause instanceof KeyStoreException) {
                    throw (KeyStoreException) cause;
                }
                if (cause instanceof CsrProcessingException) {
                    throw (CsrProcessingException) cause;
                }
            }
            throw e;
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
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void subscribeToClientCertificateUpdates(@NonNull String csr, @NonNull Consumer<X509Certificate[]> cb)
            throws KeyStoreException, CsrProcessingException {
        // BouncyCastle can throw RuntimeExceptions, and unfortunately it is not easy to detect
        // bad input beforehand. For now, just catch and re-throw a CsrProcessingException
        try {
            PKCS10CertificationRequest pkcs10CertificationRequest =
                    CertificateHelper.getPKCS10CertificationRequestFromPem(csr);
            JcaPKCS10CertificationRequest jcaRequest = new JcaPKCS10CertificationRequest(pkcs10CertificationRequest);
            CertificateGenerator certificateGenerator = new ClientCertificateGenerator(
                    jcaRequest.getSubject(), jcaRequest.getPublicKey(), cb, certificateStore, certificatesConfig);
            certificateGenerator.generateCertificate(Collections::emptyList,
                    "initialization of client cert subscription");
            certExpiryMonitor.addToMonitor(certificateGenerator);
        } catch (KeyStoreException e) {
            logger.atError().setCause(e).log("unable to subscribe to certificate update");
            throw e;
        } catch (RuntimeException | NoSuchAlgorithmException | InvalidKeyException | IOException e) {
            throw new CsrProcessingException(csr, e);
        }
    }

    /**
     * Unsubscribe from server certificate updates.
     *
     * @param cb Certificate consumer
     */
    public void unsubscribeFromServerCertificateUpdates(@NonNull Consumer<X509Certificate> cb) {
        CertificateGenerator gen = serverCertSubscriptions.remove(cb);
        if (gen != null) {
            removeCGFromMonitors(gen);
        }
    }

    private void removeCGFromMonitors(CertificateGenerator gen) {
        certExpiryMonitor.removeFromMonitor(gen);
        cisShadowMonitor.removeFromMonitor(gen);
    }
}
