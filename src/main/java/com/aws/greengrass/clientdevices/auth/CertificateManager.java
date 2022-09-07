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
import com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidCertificateAuthorityException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.clientdevices.auth.iot.ConnectivityInfoProvider;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.security.exceptions.ServiceUnavailableException;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.RetryUtils;
import lombok.NonNull;
import software.amazon.awssdk.services.greengrassv2data.model.AccessDeniedException;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.PutCertificateAuthoritiesRequest;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;

import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
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
    private final GreengrassServiceClientFactory clientFactory;
    private final SecurityService securityService;
    private CertificatesConfig certificatesConfig;
    private static final Logger logger = LogManager.getLogger(CAConfiguration.class);


    /**
     * Construct a new CertificateManager.
     *
     * @param certificateStore         Helper class for managing certificate authorities
     * @param connectivityInfoProvider Connectivity Info Provider
     * @param certExpiryMonitor        Certificate Expiry Monitor
     * @param cisShadowMonitor         CIS Shadow Monitor
     * @param clock                    clock
     * @param clientFactory            Greengrass cloud service client factory
     * @param securityService          Security Service
     */
    @Inject
    public CertificateManager(CertificateStore certificateStore,
                              ConnectivityInfoProvider connectivityInfoProvider,
                              CertificateExpiryMonitor certExpiryMonitor,
                              CISShadowMonitor cisShadowMonitor,
                              Clock clock,
                              GreengrassServiceClientFactory clientFactory,
                              SecurityService securityService) {
        this.certificateStore = certificateStore;
        this.connectivityInfoProvider = connectivityInfoProvider;
        this.certExpiryMonitor = certExpiryMonitor;
        this.cisShadowMonitor = cisShadowMonitor;
        this.clock = clock;
        this.clientFactory = clientFactory;
        this.securityService = securityService;
    }

    public void updateCertificatesConfiguration(CertificatesConfig certificatesConfig) {
        this.certificatesConfig = certificatesConfig;
    }

    /**
     * Initialize the certificate manager.
     *
     * @param caPassphrase CA Passphrase
     * @param caType certificate authority type
     * @throws KeyStoreException if unable to load the CA key store
     */
    public void generateCA(String caPassphrase, CertificateStore.CAType caType) throws KeyStoreException {
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
     * @throws KeyStoreException            if unable to retrieve the certificate
     * @throws IOException                  if unable to write certificate
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
     * Subscribe to certificate updates.
     * <p>
     * The certificate manager will save the given request and generate a new certificate under the following scenarios:
     * 1) The previous certificate is nearing expiry
     * 2) GGC connectivity information changes (for server certificates only)
     * Certificates will continue to be generated until the client calls unsubscribeFromCertificateUpdates.
     * </p>
     * An initial certificate will be generated and sent to the consumer prior to this function returning.
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
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
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

    /**
     * Configures the KeyStore to use a certificates provided from the CA configuration.
     *
     * @param configuration the component configuration
     * @throws InvalidConfigurationException if the CA configuration doesn't have both a privateKeyUri and
     *                                      certificateUri
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void configureCustomCA(CDAConfiguration configuration) throws InvalidConfigurationException {
        if (!configuration.isUsingCustomCA()) {
            throw new InvalidConfigurationException(
                    "Invalid configuration: certificateUri and privateKeyUri are required.");
        }

        URI privateKeyUri = configuration.getPrivateKeyUri().get();
        URI certificateUri = configuration.getCertificateUri().get();

        RetryUtils.RetryConfig retryConfig = RetryUtils.RetryConfig.builder()
                .initialRetryInterval(Duration.ofMillis(200)).maxAttempt(3)
                .retryableExceptions(Collections.singletonList(ServiceUnavailableException.class)).build();


        try {
            KeyPair keyPair = RetryUtils.runWithRetry(retryConfig,
                    () -> securityService.getKeyPair(privateKeyUri, certificateUri),
                    "get-key-pair", logger);

            X509Certificate[] certificateChain = RetryUtils.runWithRetry(retryConfig,
                    () -> securityService.getCertificateChain(privateKeyUri, certificateUri),
                    "get-certificate-chain", logger);

            certificateStore.setCaCertificateChain(certificateChain);
            certificateStore.setCaPrivateKey(keyPair.getPrivate());
        } catch (Exception e) {
            throw new InvalidCertificateAuthorityException(String.format("Failed to configure CA: There was an error "
                    + "reading the provided private key %s or certificate chain %s", privateKeyUri, certificateUri), e);
        }
    }

    /**
     * Uploads the stored certificates to the cloud.
     *
     * @param thingName Core device name
     * @throws CertificateEncodingException      If unable to get certificate encoding
     * @throws KeyStoreException                if unable to retrieve the certificate
     * @throws IOException                      If unable to read certificate
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void uploadCoreDeviceCAs(String thingName) throws CertificateEncodingException, KeyStoreException,
            IOException {
        List<String> certificatePemList = getCACertificates();
        List<Class> retryAbleExceptions = Arrays.asList(ThrottlingException.class, InternalServerException.class,
                AccessDeniedException.class);

        RetryUtils.RetryConfig retryConfig = RetryUtils.RetryConfig.builder()
                .initialRetryInterval(Duration.ofSeconds(3)).maxAttempt(Integer.MAX_VALUE)
                .retryableExceptions(retryAbleExceptions)
                .build();

        PutCertificateAuthoritiesRequest request =
                PutCertificateAuthoritiesRequest.builder().coreDeviceThingName(thingName)
                        .coreDeviceCertificates(certificatePemList).build();

        try {
            RetryUtils.runWithRetry(retryConfig,
                    () -> clientFactory.getGreengrassV2DataClient().putCertificateAuthorities(request),
                    "put-core-ca-certificate", logger);
        } catch (InterruptedException e) {
            logger.atInfo().log("Put core CA certificates got interrupted");
            // interrupt the current thread so that higher-level interrupt handlers can take care of it
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new CloudServiceInteractionException("Failed to put core CA certificates to cloud. Check that the "
                    + "core device's IoT policy grants the greengrass:PutCertificateAuthorities permission.", e);
        }
    }
}
