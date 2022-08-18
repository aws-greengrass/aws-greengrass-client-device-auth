/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.certificate.CISShadowMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.CertificatesConfig;
import com.aws.greengrass.clientdevices.auth.certificate.ClientCertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.ServerCertificateGenerator;
import com.aws.greengrass.clientdevices.auth.exception.CertificateAuthorityNotFoundException;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.iot.ConnectivityInfoProvider;
import com.aws.greengrass.config.Topics;
import lombok.NonNull;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.ArrayList;
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
    private final Map<GetCertificateRequest, CertificateGenerator> certSubscriptions = new ConcurrentHashMap<>();
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

    public void setCertificateStoreConfig(Topics config, Topics runtimeConfig) {
        certificateStore.setCertificateStoreConfig(config, runtimeConfig);
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
     * @throws CertificateAuthorityNotFoundException if unable to retrieve the CA
     */
    public List<String> getCACertificates() throws KeyStoreException, IOException,
            CertificateEncodingException, CertificateAuthorityNotFoundException {
        List<String> certificatePems = new ArrayList<>();
        Certificate[] certs = certificateStore.getCaCertificateChain();
        for (Certificate cert : certs) {
            certificatePems.add(CertificateHelper.toPem((X509Certificate) cert));
        }
        return certificatePems;
    }

    private X509Certificate[] getX509CACertificates() throws KeyStoreException, CertificateAuthorityNotFoundException {
        return certificateStore.getCaCertificateChain();
    }

    /**
     * Subscribe to certificate updates.
     * <p>
     * The certificate manager will save the given request and generate a new certificate under the following scenarios:
     *   1) The previous certificate is nearing expiry
     *   2) GGC connectivity information changes (for server certificates only)
     * Certificates will continue to be generated until the client calls unsubscribeFromCertificateUpdates.
     * </p>
     * An initial certificate will be generated and sent to the consumer prior to this function returning.
     * </p>
     *
     * @param getCertificateRequest get certificate request
     * @throws CertificateGenerationException if unable to generate certificate
     * @throws CertificateAuthorityNotFoundException if unable to retrieve the CA
     */
    public void subscribeToCertificateUpdates(GetCertificateRequest getCertificateRequest)
            throws CertificateGenerationException {
        try {
            GetCertificateRequestOptions.CertificateType certificateType =
                    getCertificateRequest.getCertificateRequestOptions().getCertificateType();
            // TODO: Should be configurable
            KeyPair keyPair = CertificateStore.newRSAKeyPair(4096);

            if (certificateType.equals(GetCertificateRequestOptions.CertificateType.SERVER)) {
                KeyPair finalKeyPair = keyPair;
                X509Certificate[] caCertificates = getX509CACertificates();
                Consumer<X509Certificate> consumer = (t) -> {
                    CertificateUpdateEvent certificateUpdateEvent =
                            new CertificateUpdateEvent(finalKeyPair, t, caCertificates);
                    getCertificateRequest.getCertificateUpdateConsumer().accept(certificateUpdateEvent);
                };
                subscribeToServerCertificateUpdatesNoCSR(getCertificateRequest, keyPair.getPublic(), consumer);
            } else if (certificateType.equals(GetCertificateRequestOptions.CertificateType.CLIENT)) {
                KeyPair finalKeyPair = keyPair;
                X509Certificate[] caCertificates = getX509CACertificates();
                Consumer<X509Certificate[]> consumer = (t) -> {
                    CertificateUpdateEvent certificateUpdateEvent =
                            new CertificateUpdateEvent(finalKeyPair, t[0], caCertificates);
                    getCertificateRequest.getCertificateUpdateConsumer().accept(certificateUpdateEvent);
                };
                subscribeToClientCertificateUpdatesNoCSR(getCertificateRequest, keyPair.getPublic(), consumer);
            }
        } catch (NoSuchAlgorithmException | KeyStoreException | CertificateAuthorityNotFoundException e) {
            throw new CertificateGenerationException(e);
        }
    }

    /**
     * Unsubscribe from certificate updates.
     *
     * @param getCertificateRequest get certificate request object used to make the initial subscription request
     */
    public void unsubscribeFromCertificateUpdates(GetCertificateRequest getCertificateRequest) {
        CertificateGenerator certGen = certSubscriptions.remove(getCertificateRequest);
        if (certGen != null) {
            removeCGFromMonitors(certGen);
        }
    }

    private void subscribeToServerCertificateUpdatesNoCSR(@NonNull GetCertificateRequest certificateRequest,
                                                          @NonNull PublicKey publicKey,
                                                          @NonNull Consumer<X509Certificate> cb)
            throws CertificateGenerationException {
        CertificateGenerator certificateGenerator =
                new ServerCertificateGenerator(
                        CertificateHelper.getX500Name(certificateRequest.getServiceName()),
                        publicKey, cb, certificateStore, certificatesConfig, clock);

        // Add certificate generator to monitors first in order to avoid missing events
        // that happen while the initial certificate is being generated.
        certExpiryMonitor.addToMonitor(certificateGenerator);
        cisShadowMonitor.addToMonitor(certificateGenerator);

        certificateGenerator.generateCertificate(connectivityInfoProvider::getCachedHostAddresses,
                "initialization of server cert subscription");

        certSubscriptions.compute(certificateRequest, (k, v) -> {
            // A subscription already exists, we will replace it so that a new certificate is generated immediately
            if (v != null) {
                removeCGFromMonitors(v);
            }
            return certificateGenerator;
        });
    }

    private void subscribeToClientCertificateUpdatesNoCSR(@NonNull GetCertificateRequest certificateRequest,
                                                          @NonNull PublicKey publicKey,
                                                          @NonNull Consumer<X509Certificate[]> cb)
            throws CertificateGenerationException {
        CertificateGenerator certificateGenerator =
                new ClientCertificateGenerator(
                        CertificateHelper.getX500Name(certificateRequest.getServiceName()),
                        publicKey, cb, certificateStore, certificatesConfig, clock);

        certExpiryMonitor.addToMonitor(certificateGenerator);
        certificateGenerator.generateCertificate(Collections::emptyList,
                "initialization of client cert subscription");

        certSubscriptions.compute(certificateRequest, (k, v) -> {
            // A subscription already exists, we will replace it so that a new certificate is generated immediately
            if (v != null) {
                removeCGFromMonitors(v);
            }
            return certificateGenerator;
        });
    }

    private void removeCGFromMonitors(CertificateGenerator gen) {
        certExpiryMonitor.removeFromMonitor(gen);
        cisShadowMonitor.removeFromMonitor(gen);
    }
}
