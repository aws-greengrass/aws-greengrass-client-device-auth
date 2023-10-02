/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestWithGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.CertificatesConfig;
import com.aws.greengrass.clientdevices.auth.certificate.ClientCertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.ServerCertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.events.CertificateSubscriptionEvent;
import com.aws.greengrass.clientdevices.auth.certificate.handlers.CertificateRotationHandler;
import com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration;
import com.aws.greengrass.clientdevices.auth.connectivity.CISShadowMonitor;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.exception.CertificateChainLoadingException;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidCertificateAuthorityException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.security.exceptions.ServiceUnavailableException;
import com.aws.greengrass.util.EncryptionUtils;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.RetryUtils;
import lombok.Getter;
import lombok.NonNull;
import software.amazon.awssdk.services.greengrassv2data.model.AccessDeniedException;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.PutCertificateAuthoritiesRequest;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.inject.Inject;

public class CertificateManager {
    private static final Logger logger = LogManager.getLogger(CertificateManager.class);
    private static final String pkcs11Scheme = "pkcs11";
    private final CertificateStore certificateStore;
    private final ConnectivityInformation connectivityInformation;
    private final CertificateExpiryMonitor certExpiryMonitor;
    private final CISShadowMonitor cisShadowMonitor;
    private final CertificateRotationHandler caConfigurationMonitor;
    @Getter
    private final Clock clock;
    private final Map<GetCertificateRequest, CertificateGenerator> certSubscriptions = new ConcurrentHashMap<>();
    private final GreengrassServiceClientFactory clientFactory;
    private final SecurityService securityService;
    private final DomainEvents domainEvent;
    @Getter
    private CertificatesConfig certificatesConfig;

    /**
     * Construct a new CertificateManager.
     *
     * @param certificateStore        Helper class for managing certificate authorities
     * @param connectivityInformation Connectivity Info Provider
     * @param certExpiryMonitor       Certificate Expiry Monitor
     * @param cisShadowMonitor        CIS Shadow Monitor
     * @param clock                   clock
     * @param clientFactory           Greengrass cloud service client factory
     * @param securityService         Security Service
     * @param caConfigurationMonitor  CA Configuration Monitor
     * @param domainEvent             Metric event emitter
     */
    @Inject
    public CertificateManager(CertificateStore certificateStore, ConnectivityInformation connectivityInformation,
                              CertificateExpiryMonitor certExpiryMonitor, CISShadowMonitor cisShadowMonitor,
                              Clock clock, GreengrassServiceClientFactory clientFactory,
                              SecurityService securityService, CertificateRotationHandler caConfigurationMonitor,
                              DomainEvents domainEvent) {
        this.certificateStore = certificateStore;
        this.connectivityInformation = connectivityInformation;
        this.certExpiryMonitor = certExpiryMonitor;
        this.cisShadowMonitor = cisShadowMonitor;
        this.caConfigurationMonitor = caConfigurationMonitor;
        this.clock = clock;
        this.clientFactory = clientFactory;
        this.securityService = securityService;
        this.domainEvent = domainEvent;
    }

    public void updateCertificatesConfiguration(CertificatesConfig certificatesConfig) {
        this.certificatesConfig = certificatesConfig;
    }

    /**
     * Initialize the certificate manager.
     *
     * @param caPassphrase CA Passphrase
     * @param caType       certificate authority type
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
     * 1) The previous certificate is nearing expiry 2) GGC connectivity information changes (for server certificates
     * only) Certificates will continue to be generated until the client calls unsubscribeFromCertificateUpdates.
     * </p>
     * An initial certificate will be generated and sent to the consumer prior to this function returning.
     * </p>
     *
     * @param getCertificateRequest get certificate request
     * @throws CertificateGenerationException if unable to generate certificate
     */
    public void subscribeToCertificateUpdates(GetCertificateRequest getCertificateRequest)
            throws CertificateGenerationException {
        GetCertificateRequestOptions.CertificateType certificateType =
                getCertificateRequest.getCertificateRequestOptions().getCertificateType();
        try {
            CertificateGenerator certificateGenerator =
                    createCertificateGenerator(getCertificateRequest, certificateType);
            subscribeToCertificateUpdatesNoCSR(certificateGenerator, getCertificateRequest, certificateType);

            if (GetCertificateRequestOptions.CertificateType.SERVER.equals(certificateType)) {
                logger.atDebug().log("Successfully subscribed to certificate update");
                domainEvent.emit(new CertificateSubscriptionEvent(certificateType,
                        CertificateSubscriptionEvent.SubscriptionStatus.SUCCESS));
            }
        } catch (NoSuchAlgorithmException e) {
            domainEvent.emit(new CertificateSubscriptionEvent(certificateType,
                    CertificateSubscriptionEvent.SubscriptionStatus.FAIL));
            throw new CertificateGenerationException(e);
        }
    }

    private CertificateGenerator createCertificateGenerator(GetCertificateRequest request,
                                                            GetCertificateRequestOptions.CertificateType type)
            throws CertificateGenerationException, NoSuchAlgorithmException {

        switch (type) {
            case SERVER:
            case CLIENT:
                // TODO: Should be configurable
                KeyPair keyPair = CertificateStore.newRSAKeyPair(4096);

                BiConsumer<X509Certificate, X509Certificate[]> consumer = (cert, caCertificates) -> {
                    CertificateUpdateEvent certificateUpdateEvent =
                            new CertificateUpdateEvent(keyPair, cert, caCertificates);
                    request.getCertificateUpdateConsumer().accept(certificateUpdateEvent);
                };
                if (GetCertificateRequestOptions.CertificateType.SERVER.equals(type)) {
                    return new ServerCertificateGenerator(CertificateHelper.getX500Name(request.getServiceName()),
                            keyPair.getPublic(), consumer, certificateStore, certificatesConfig, clock);
                } else {
                    return new ClientCertificateGenerator(CertificateHelper.getX500Name(request.getServiceName()),
                            keyPair.getPublic(), consumer, certificateStore, certificatesConfig, clock);
                }
            case CLIENT_AND_SERVER:
                // the attached certificate generator is responsible for getting the certificate store and clock
                if (request instanceof GetCertificateRequestWithGenerator) {
                    return ((GetCertificateRequestWithGenerator) request).getCertificateGenerator();
                }
                throw new CertificateGenerationException("Invalid certificate request for type " + type);
            default:
                throw new CertificateGenerationException("Unsupported certificate type: " + type);
        }
    }

    private void subscribeToCertificateUpdatesNoCSR(@NonNull CertificateGenerator certificateGenerator,
                                                    @NonNull GetCertificateRequest certificateRequest,
                                                    @NonNull GetCertificateRequestOptions.CertificateType type)
            throws CertificateGenerationException {
        // Add certificate generator to monitors
        certExpiryMonitor.addToMonitor(certificateGenerator);
        caConfigurationMonitor.addToMonitor(certificateGenerator);

        if (type == GetCertificateRequestOptions.CertificateType.SERVER
                || type == GetCertificateRequestOptions.CertificateType.CLIENT_AND_SERVER) {
            // monitor connectivity info changes from cloud
            cisShadowMonitor.addToMonitor(certificateGenerator);
        }

        if (certificateStore.isReady()) {
            String reason = "initialization of " + type.toString().toLowerCase() + " cert subscription";
            certificateGenerator.generateCertificate(getAddressProvider(type), reason);
        }

        certSubscriptions.compute(certificateRequest, (k, v) -> {
            if (v != null) {
                removeCGFromMonitors(v);
            }
            return certificateGenerator;
        });
    }

    private Supplier<List<String>> getAddressProvider(GetCertificateRequestOptions.CertificateType type) {
        if (type == GetCertificateRequestOptions.CertificateType.CLIENT) {
            return Collections::emptyList;
        }
        return connectivityInformation::getCachedHostAddresses;
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

    private void removeCGFromMonitors(CertificateGenerator gen) {
        certExpiryMonitor.removeFromMonitor(gen);
        cisShadowMonitor.removeFromMonitor(gen);
        caConfigurationMonitor.removeFromMonitor(gen);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private X509Certificate[] getCertificateChainFromConfiguration(CAConfiguration configuration)
            throws CertificateChainLoadingException {
        try {
            Optional<URI> certificateChainUri = configuration.getCertificateChainUri();

            if (certificateChainUri.isPresent()) {
                List<X509Certificate> certificateChain =
                        EncryptionUtils.loadX509Certificates(Paths.get(certificateChainUri.get()));
                return certificateChain.toArray(new X509Certificate[0]);
            }

            URI certificateUri = configuration.getCertificateUri().get();
            URI privateKeyUri = configuration.getPrivateKeyUri().get();

            RetryUtils.RetryConfig retryConfig =
                    RetryUtils.RetryConfig.builder().initialRetryInterval(Duration.ofSeconds(2)).maxAttempt(3)
                            .retryableExceptions(Collections.singletonList(ServiceUnavailableException.class)).build();

            return RetryUtils.runWithRetry(retryConfig,
                    () -> certificateStore.loadCaCertificateChain(privateKeyUri, certificateUri),
                    "get-certificate-chain", logger);
        } catch (Exception e) {
            throw new CertificateChainLoadingException("Failed to load certificate chain", e);
        }
    }

    /**
     * Configures the KeyStore to use a certificates provided from the CA configuration.
     *
     * @param configuration the certificateAuthority configuration
     * @throws InvalidConfigurationException if the CA configuration doesn't have both a privateKeyUri and
     *                                       certificateUri
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public void configureCustomCA(CAConfiguration configuration) throws InvalidConfigurationException {
        if (!configuration.isUsingCustomCA()) {
            throw new InvalidConfigurationException(
                    "Invalid configuration: certificateUri and privateKeyUri are required.");
        }

        URI privateKeyUri = configuration.getPrivateKeyUri().get();
        URI certificateUri = configuration.getCertificateUri().get();

        RetryUtils.RetryConfig retryConfig =
                RetryUtils.RetryConfig.builder().initialRetryInterval(Duration.ofSeconds(2)).maxAttempt(3)
                        .retryableExceptions(Collections.singletonList(ServiceUnavailableException.class)).build();

        logger.atInfo().kv("privateKeyUri", privateKeyUri).kv("certificateUri", certificateUri)
                .log("Configuring custom core CA");

        try {
            KeyPair keyPair = RetryUtils.runWithRetry(retryConfig,
                    () -> securityService.getKeyPair(privateKeyUri, certificateUri), "get-key-pair", logger);

            X509Certificate[] certificateChain = getCertificateChainFromConfiguration(configuration);

            CertificateHelper.ProviderType providerType =
                    privateKeyUri.getScheme().contains(pkcs11Scheme) ? CertificateHelper.ProviderType.HSM
                            : CertificateHelper.ProviderType.DEFAULT;

            certificateStore.setCaKeyAndCertificateChain(providerType, keyPair.getPrivate(), certificateChain);
        } catch (Exception e) {
            throw new InvalidCertificateAuthorityException(String.format("Failed to configure CA: There was an error "
                    + "reading the provided private key %s or certificate chain %s", privateKeyUri, certificateUri), e);
        }
    }

    /**
     * Uploads the stored certificates to the cloud.
     *
     * @param thingName   Core device name
     * @param certificate the CA to upload to IoT core. Which will be provided by cloud discovery
     * @throws CertificateEncodingException If unable to get certificate encoding
     * @throws KeyStoreException            If unable to retrieve the certificate
     * @throws IOException                  If unable to read certificate
     * @throws DeviceConfigurationException If unable to retrieve Greengrass V2 Data client
     */
    @SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.AvoidRethrowingException"})
    public void uploadCoreDeviceCAs(String thingName, X509Certificate certificate)
            throws CertificateEncodingException, KeyStoreException, IOException, DeviceConfigurationException {
        String certificatePem = CertificateHelper.toPem(certificate);

        List<Class> retryAbleExceptions =
                Arrays.asList(ThrottlingException.class, InternalServerException.class, AccessDeniedException.class);

        RetryUtils.RetryConfig retryConfig =
                RetryUtils.RetryConfig.builder().initialRetryInterval(Duration.ofSeconds(3))
                        .maxAttempt(Integer.MAX_VALUE).retryableExceptions(retryAbleExceptions).build();

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