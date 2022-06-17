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
import com.aws.greengrass.device.api.GetCertificateRequest;
import com.aws.greengrass.device.api.GetCertificateRequestOptions;
import com.aws.greengrass.device.api.GetCertificateUpdate;
import com.aws.greengrass.device.exception.CertificateGenerationException;
import lombok.NonNull;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.inject.Inject;

public class CertificateManager {
    private final CertificateStore certificateStore;

    private final ConnectivityInfoProvider connectivityInfoProvider;

    private final CertificateExpiryMonitor certExpiryMonitor;

    private final CISShadowMonitor cisShadowMonitor;

    private final Clock clock;

    private final Map<String, CertificateGenerator> serverCertSubscriptions =
            new ConcurrentHashMap<>();

    private CertificatesConfig certificatesConfig;

    /**
     * Construct a new CertificateManager.
     *
     * @param certificateStore         Helper class for managing certificate authorities
     * @param connectivityInfoProvider Connectivity Info Provider
     * @param certExpiryMonitor        Certificate Expiry Monitor
     * @param cisShadowMonitor         CIS Shadow Monitor
     * @param clock                    clock
     */
    @Inject
    public CertificateManager(CertificateStore certificateStore,
                              ConnectivityInfoProvider connectivityInfoProvider,
                              CertificateExpiryMonitor certExpiryMonitor,
                              CISShadowMonitor cisShadowMonitor,
                              Clock clock) {
        this.certificateStore = certificateStore;
        this.connectivityInfoProvider = connectivityInfoProvider;
        this.certExpiryMonitor = certExpiryMonitor;
        this.cisShadowMonitor = cisShadowMonitor;
        this.clock = clock;
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

    private X509Certificate[] getX509CACertificates() throws KeyStoreException {
        return new X509Certificate[]{certificateStore.getCACertificate()};
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
     * @param getCertificateRequest get certificate request
     * @throws CertificateGenerationException if unable to generate certificate
     */
    public void subscribeToCertificateUpdates(GetCertificateRequest getCertificateRequest)
            throws CertificateGenerationException {
        try {
            GetCertificateRequestOptions.CertificateType certificateType =
                    getCertificateRequest.getCertificateRequestOptions().getCertificateType();
            X509Certificate[] caCertificates = getX509CACertificates();
            KeyPair keyPair = CertificateStore.newRSAKeyPair(2048);

            if (certificateType.equals(GetCertificateRequestOptions.CertificateType.SERVER)) {
                KeyPair finalKeyPair = keyPair;
                Consumer<X509Certificate> consumer = (t) -> {
                    GetCertificateUpdate getCertificateUpdate =
                            new GetCertificateUpdate(finalKeyPair, t, caCertificates);
                    getCertificateRequest.getCertificateUpdateConsumer().accept(getCertificateUpdate);
                };
                subscribeToServerCertificateUpdatesNoCSR(getCertificateRequest.getServiceName(),
                        keyPair.getPublic(), consumer);
            } else if (certificateType.equals(GetCertificateRequestOptions.CertificateType.CLIENT)) {
                KeyPair finalKeyPair = keyPair;
                Consumer<X509Certificate[]> consumer = (t) -> {
                    GetCertificateUpdate getCertificateUpdate =
                            new GetCertificateUpdate(finalKeyPair, t[0], caCertificates);
                    getCertificateRequest.getCertificateUpdateConsumer().accept(getCertificateUpdate);
                };
                subscribeToClientCertificateUpdatesNoCSR(getCertificateRequest.getServiceName(),
                        keyPair.getPublic(), consumer);
            }
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new CertificateGenerationException(e);
        }
    }

    public void unsubscribeFromCertificateUpdates(GetCertificateRequest getCertificateRequest) {
        // TODO
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
     * @throws CsrProcessingException if unable to process CSR
     * @throws RuntimeException for any unknown runtime error
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Deprecated
    public void subscribeToServerCertificateUpdates(@NonNull String csr, @NonNull Consumer<X509Certificate> cb)
            throws CsrProcessingException {
        try {
            PKCS10CertificationRequest pkcs10CertificationRequest =
                    CertificateHelper.getPKCS10CertificationRequestFromPem(csr);
            JcaPKCS10CertificationRequest jcaRequest =
                    new JcaPKCS10CertificationRequest(pkcs10CertificationRequest);

            String subject = "temp"; // This is a temporary change, so don't bother properly extracting CN
            subscribeToServerCertificateUpdatesNoCSR(subject, jcaRequest.getPublicKey(), cb);
        } catch (CertificateGenerationException | IOException | NoSuchAlgorithmException | InvalidKeyException
                | RuntimeException e) {
            throw new CsrProcessingException(csr, e);
        }
    }

    private void subscribeToServerCertificateUpdatesNoCSR(@NonNull String serviceName,
                                                         @NonNull PublicKey publicKey,
                                                         @NonNull Consumer<X509Certificate> cb)
            throws CertificateGenerationException {
        CertificateGenerator certificateGenerator =
                new ServerCertificateGenerator(CertificateHelper.getIssuer(serviceName), publicKey, cb,
                        certificateStore, certificatesConfig, clock);

        // Add certificate generator to monitors first in order to avoid missing events
        // that happen while the initial certificate is being generated.
        certExpiryMonitor.addToMonitor(certificateGenerator);
        cisShadowMonitor.addToMonitor(certificateGenerator);

        certificateGenerator.generateCertificate(connectivityInfoProvider::getCachedHostAddresses,
                "initialization of server cert subscription");

        serverCertSubscriptions.compute(serviceName, (k, v) -> {
            // A subscription already exists, we will replace it so that a new certificate is generated immediately
            if (v != null) {
                removeCGFromMonitors(v);
            }
            return certificateGenerator;
        });
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
     * @throws CsrProcessingException if unable to process CSR
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Deprecated
    public void subscribeToClientCertificateUpdates(@NonNull String csr, @NonNull Consumer<X509Certificate[]> cb)
            throws CsrProcessingException {
        // BouncyCastle can throw RuntimeExceptions, and unfortunately it is not easy to detect
        // bad input beforehand. For now, just catch and re-throw a CsrProcessingException
        try {
            PKCS10CertificationRequest pkcs10CertificationRequest =
                    CertificateHelper.getPKCS10CertificationRequestFromPem(csr);
            JcaPKCS10CertificationRequest jcaRequest = new JcaPKCS10CertificationRequest(pkcs10CertificationRequest);
            String subject = "temp"; // This is a temporary change so don't bother properly extracting CN
            subscribeToClientCertificateUpdatesNoCSR(subject, jcaRequest.getPublicKey(), cb);
        } catch (RuntimeException | NoSuchAlgorithmException | InvalidKeyException | IOException
                | CertificateGenerationException e) {
            throw new CsrProcessingException(csr, e);
        }
    }

    private void subscribeToClientCertificateUpdatesNoCSR(@NonNull String serviceName,
                                                         @NonNull PublicKey publicKey,
                                                         @NonNull Consumer<X509Certificate[]> cb)
            throws CertificateGenerationException {
        CertificateGenerator certificateGenerator =
                new ClientCertificateGenerator(CertificateHelper.getIssuer(serviceName), publicKey, cb,
                        certificateStore, certificatesConfig, clock);
        certificateGenerator.generateCertificate(Collections::emptyList,
                "initialization of client cert subscription");
        certExpiryMonitor.addToMonitor(certificateGenerator);
    }

    /**
     * Unsubscribe from server certificate updates.
     *
     * @param cb Certificate consumer
     */
    @Deprecated
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
