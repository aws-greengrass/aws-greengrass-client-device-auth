/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.CertificatesConfig;
import com.aws.greengrass.clientdevices.auth.certificate.ClientCertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.ServerCertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.handlers.CertificateRotationHandler;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.connectivity.CISShadowMonitor;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidCertificateAuthorityException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
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
import java.util.function.BiConsumer;
import javax.inject.Inject;

public class CertificateManager {
    private final CertificateStore certificateStore;
    private final ConnectivityInformation connectivityInformation;
    private final CertificateExpiryMonitor certExpiryMonitor;
    private final CISShadowMonitor cisShadowMonitor;
    private final CertificateRotationHandler caConfigurationMonitor;
    private final Clock clock;
    private final Map<GetCertificateRequest, CertificateGenerator> certSubscriptions = new ConcurrentHashMap<>();
    private final GreengrassServiceClientFactory clientFactory;
    private final SecurityService securityService;
    private CertificatesConfig certificatesConfig;
    private static final Logger logger = LogManager.getLogger(CertificateManager.class);


    /**
     * Construct a new CertificateManager.
     *
     * @param certificateStore        Helper class for managing certificate authorities
     * @param connectivityInformation Connectivity Info Provider
     * @param certExpiryMonitor       Certificate Expiry Monitor
     * @param cisShadowMonitor        CIS Shadow Monitor
     * @param clock                   clock
     * @param clientFactory           Greengrass cloud service client factory
     * @param securityService          Security Service
     * @param caConfigurationMonitor   CA Configuration Monitor
     */
    @Inject
    public CertificateManager(CertificateStore certificateStore,
                              ConnectivityInformation connectivityInformation,
                              CertificateExpiryMonitor certExpiryMonitor,
                              CISShadowMonitor cisShadowMonitor,
                              Clock clock,
                              GreengrassServiceClientFactory clientFactory,
                              SecurityService securityService,
                              CertificateRotationHandler caConfigurationMonitor) {
        this.certificateStore = certificateStore;
        this.connectivityInformation = connectivityInformation;
        this.certExpiryMonitor = certExpiryMonitor;
        this.cisShadowMonitor = cisShadowMonitor;
        this.caConfigurationMonitor = caConfigurationMonitor;
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
     * Returns a singleton list with the PEM encoded CA at position 0 of the CA chain.
     *
     * @throws KeyStoreException            if unable to retrieve the certificate
     * @throws IOException                  if unable to write certificate
     * @throws CertificateEncodingException if unable to get certificate encoding
     *
     * @deprecated use getX509CACertificates
     */
    public List<String> getCACertificates() throws KeyStoreException, IOException, CertificateEncodingException {
        return Collections.singletonList(CertificateHelper.toPem(certificateStore.getCACertificate()));
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
                BiConsumer<X509Certificate, X509Certificate[]> consumer = (serverCert, caCertificates) -> {
                    CertificateUpdateEvent certificateUpdateEvent =
                            new CertificateUpdateEvent(keyPair, serverCert, caCertificates);
                    getCertificateRequest.getCertificateUpdateConsumer().accept(certificateUpdateEvent);
                };
                subscribeToServerCertificateUpdatesNoCSR(getCertificateRequest, keyPair.getPublic(), consumer);
            } else if (certificateType.equals(GetCertificateRequestOptions.CertificateType.CLIENT)) {
                BiConsumer<X509Certificate, X509Certificate[]> consumer = (clientCert, caCertificates) -> {
                    CertificateUpdateEvent certificateUpdateEvent =
                            new CertificateUpdateEvent(keyPair, clientCert, caCertificates);
                    getCertificateRequest.getCertificateUpdateConsumer().accept(certificateUpdateEvent);
                };
                subscribeToClientCertificateUpdatesNoCSR(getCertificateRequest, keyPair.getPublic(), consumer);
            }
        } catch (NoSuchAlgorithmException e) {
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
                                                          @NonNull BiConsumer<X509Certificate, X509Certificate[]> cb)
            throws CertificateGenerationException {
        CertificateGenerator certificateGenerator =
                new ServerCertificateGenerator(
                        CertificateHelper.getX500Name(certificateRequest.getServiceName()),
                        publicKey, cb, certificateStore, certificatesConfig, clock);

        // Add certificate generator to monitors first in order to avoid missing events
        // that happen while the initial certificate is being generated.
        certExpiryMonitor.addToMonitor(certificateGenerator);
        cisShadowMonitor.addToMonitor(certificateGenerator);
        caConfigurationMonitor.addToMonitor(certificateGenerator);

        // TODO: Doing this here is wrong. We are assuming that we can call this and it would work but this should
        //  only be called after we are sure a certificate authority has been configured. Which might not be true
        //  before this gets called. Having this fail several times in a row (if no CA has been configured) will cause
        //  downstream components that rely on this to fail
        certificateGenerator.generateCertificate(connectivityInformation::getCachedHostAddresses,
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
                                                          @NonNull BiConsumer<X509Certificate, X509Certificate[]> cb)
            throws CertificateGenerationException {
        CertificateGenerator certificateGenerator =
                new ClientCertificateGenerator(
                        CertificateHelper.getX500Name(certificateRequest.getServiceName()),
                        publicKey, cb, certificateStore, certificatesConfig, clock);

        caConfigurationMonitor.addToMonitor(certificateGenerator);
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
        caConfigurationMonitor.removeFromMonitor(gen);
    }

    /**
     * Configures the KeyStore to use a certificates provided from the CA configuration.
     *
     * @param configuration                the certificateAuthority configuration
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
                .initialRetryInterval(Duration.ofSeconds(5)).maxAttempt(3)
                .retryableExceptions(Collections.singletonList(ServiceUnavailableException.class)).build();

        logger.atInfo().kv("privateKeyUri", privateKeyUri).kv("certificateUri", certificateUri)
                .log("Configuring custom core CA");

        try {
            KeyPair keyPair = RetryUtils.runWithRetry(retryConfig,
                    () -> securityService.getKeyPair(privateKeyUri, certificateUri),
                    "get-key-pair", logger);

            X509Certificate[] certificateChain = RetryUtils.runWithRetry(retryConfig,
                    () -> certificateStore.loadCaCertificateChain(privateKeyUri, certificateUri),
                    "get-certificate-chain", logger);

            certificateStore.setCaKeyAndCertificateChain(keyPair.getPrivate(), certificateChain);
        } catch (Exception e) {
            throw new InvalidCertificateAuthorityException(String.format("Failed to configure CA: There was an error "
                    + "reading the provided private key %s or certificate chain %s", privateKeyUri, certificateUri), e);
        }
    }

    /**
     * Uploads the stored certificates to the cloud.
     *
     * @param thingName Core device name
     * @param certificate the CA to upload to IoT core. Which will be provided by cloud discovery
     *
     * @throws CertificateEncodingException   If unable to get certificate encoding
     * @throws KeyStoreException              If unable to retrieve the certificate
     * @throws IOException                    If unable to read certificate
     * @throws DeviceConfigurationException   If unable to retrieve Greengrass V2 Data client
     */
    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.AvoidRethrowingException"})
    public void uploadCoreDeviceCAs(String thingName, X509Certificate certificate)
            throws CertificateEncodingException, KeyStoreException,
            IOException, DeviceConfigurationException {
        String certificatePem = CertificateHelper.toPem(certificate);

        List<Class> retryAbleExceptions = Arrays.asList(ThrottlingException.class, InternalServerException.class,
                AccessDeniedException.class);

        RetryUtils.RetryConfig retryConfig = RetryUtils.RetryConfig.builder()
                .initialRetryInterval(Duration.ofSeconds(3)).maxAttempt(Integer.MAX_VALUE)
                .retryableExceptions(retryAbleExceptions)
                .build();

        PutCertificateAuthoritiesRequest request =
                PutCertificateAuthoritiesRequest.builder().coreDeviceThingName(thingName)
                        .coreDeviceCertificates(Collections.singletonList(certificatePem)).build();

        try {
            RetryUtils.runWithRetry(retryConfig,
                    () -> clientFactory.fetchGreengrassV2DataClient().putCertificateAuthorities(request),
                    "put-core-ca-certificate", logger);
        } catch (InterruptedException e) {
            logger.atInfo().log("Put core CA certificates got interrupted");
            // interrupt the current thread so that higher-level interrupt handlers can take care of it
            Thread.currentThread().interrupt();
        } catch (DeviceConfigurationException e) {
            // Need to explicitly catch and re-throw so this doesn't get eaten
            // by the next catch block
            throw e;
        } catch (Exception e) {
            throw new CloudServiceInteractionException("Failed to put core CA certificates to cloud. Check that the "
                    + "core device's IoT policy grants the greengrass:PutCertificateAuthorities permission.", e);
        }
    }
}