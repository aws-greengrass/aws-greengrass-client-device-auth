/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.certificateauthority;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.DeviceAuthClient;
import com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.CertificatesConfig;
import com.aws.greengrass.clientdevices.auth.certificate.usecases.ConfigureCustomCertificateAuthority;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.connectivity.CISShadowMonitor;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.iot.registry.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.session.SessionManager;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.security.exceptions.CertificateChainLoadingException;
import com.aws.greengrass.security.exceptions.KeyLoadingException;
import com.aws.greengrass.security.exceptions.ServiceUnavailableException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.TestUtils;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.Pair;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_CERTIFICATE_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_PRIVATE_KEY_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CERTIFICATE_AUTHORITY_TOPIC;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CustomCaConfigurationTest {
    @Mock
    private SessionManager sessionManagerMock;

    @Mock
    private GroupManager groupManagerMock;

    @Mock private CertificateRegistry certificateRegistryMock;

    @Mock
    ConnectivityInformation mockConnectivityInformation;

    @Mock
    CertificateExpiryMonitor mockCertExpiryMonitor;

    @Mock
    CISShadowMonitor mockShadowMonitor;

    @Mock
    GreengrassServiceClientFactory clientFactoryMock;
    @Mock
    SecurityService securityServiceMock;
    @TempDir
    Path tmpPath;

    private CertificateManager certificateManager;
    private CertificateStore certificateStore;


    @BeforeEach
    void beforeEach() {
        DomainEvents events = new DomainEvents();
        certificateStore = new CertificateStore(tmpPath, events);

        certificateManager = new CertificateManager(
                certificateStore, mockConnectivityInformation,
                mockCertExpiryMonitor, mockShadowMonitor, Clock.systemUTC(), clientFactoryMock);

    }

    /**
     * Simulates an external party configuring their custom CA.
     */
    private X509Certificate[] arrangeCustomCertificateAuthority() throws NoSuchAlgorithmException, CertificateException,
            OperatorCreationException, CertIOException, URISyntaxException, KeyLoadingException,
            ServiceUnavailableException, CertificateChainLoadingException, InvalidConfigurationException {
        // Generate custom certificates
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.issueCertificate(
                new X500Name("CN=root"), new X500Name("CN=root"), rootKeyPair, rootKeyPair, true);

        KeyPair intermediateKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate intermediateCA = CertificateTestHelpers.issueCertificate(
                new X500Name("CN=root"), new X500Name("CN=intermediate"), intermediateKeyPair, rootKeyPair, true);

        // Service Configuration
        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");

        Topics topics = Topics.of(new Context(), CLIENT_DEVICES_AUTH_SERVICE_NAME, null);
        topics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_PRIVATE_KEY_URI)
                .withValue(privateKeyUri.toString());
        topics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_CERTIFICATE_URI)
                .withValue(certificateUri.toString());

        UseCases useCases = new UseCases();
        useCases.init(topics.getContext());
        ConfigureCustomCertificateAuthority useCase = useCases.get(ConfigureCustomCertificateAuthority.class);

        CDAConfiguration cdaConfiguration = CDAConfiguration.from(topics);
        CertificatesConfig certsConfig = new CertificatesConfig(topics);
        certificateManager.updateCertificatesConfiguration(certsConfig);

        // Configure Custom CA
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(intermediateKeyPair);
        when(securityServiceMock.getCertificateChain(privateKeyUri, certificateUri))
                .thenReturn(new X509Certificate[]{intermediateCA, rootCA});
        useCase.apply(cdaConfiguration);

        return new X509Certificate[]{intermediateCA, rootCA};
    }


    /**
     * Simulates the request client components make to create their certificates. Clients pass their certificate
     * chain pem to get verified.
     */
    private X509Certificate[] arrangeClientComponentCertificateChain() throws CertificateGenerationException,
            ExecutionException, InterruptedException, TimeoutException {
        // Generate Client Certificates
        AtomicReference<CertificateUpdateEvent> eventRef = new AtomicReference<>();

        Pair<CompletableFuture<Void>, Consumer<CertificateUpdateEvent>> asyncCall =
                TestUtils.asyncAssertOnConsumer(eventRef::set, 1);
        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.CLIENT);
        GetCertificateRequest certificateRequest =
                new GetCertificateRequest("com.aws.clients.Plugin", requestOptions, asyncCall.getRight());

        certificateManager.subscribeToCertificateUpdates(certificateRequest);
        asyncCall.getLeft().get(1, TimeUnit.SECONDS);

        // Arrange the certificate chain leaf comes first, root comes last
        return ArrayUtils.addAll(new X509Certificate[]{eventRef.get().getCertificate()},
                eventRef.get().getCaCertificates());
    }


    @Test
    void Given_CustomCAConfiguration_WHEN_issuingAClientCertificate_THEN_itsSignedByCustomCA() throws
            CertificateChainLoadingException, KeyLoadingException, CertificateException, NoSuchAlgorithmException,
            URISyntaxException, ServiceUnavailableException, OperatorCreationException, CertIOException,
            InvalidConfigurationException, CertificateGenerationException, ExecutionException, InterruptedException,
            TimeoutException {
        X509Certificate[] caCertificates = arrangeCustomCertificateAuthority();
        X509Certificate[] clientCertificateChain = arrangeClientComponentCertificateChain();

        assertTrue(CertificateTestHelpers.wasCertificateIssuedBy(caCertificates[0], clientCertificateChain[0]));
    }

    @Test
    void GIVEN_CustomCAConfiguration_WHEN_whenGeneratingClientCerts_THEN_GGComponentIsVerified() throws
            NoSuchAlgorithmException, CertificateException, OperatorCreationException, IOException,
            URISyntaxException, InvalidConfigurationException, KeyLoadingException, ServiceUnavailableException,
            CertificateChainLoadingException, CertificateGenerationException, ExecutionException, InterruptedException,
            TimeoutException {
        this.arrangeCustomCertificateAuthority();
        X509Certificate[] clientCertificateChain = arrangeClientComponentCertificateChain();

        DeviceAuthClient deviceAuth = new DeviceAuthClient(sessionManagerMock, groupManagerMock, certificateStore);
        ClientDevicesAuthServiceApi api = new ClientDevicesAuthServiceApi(
                certificateRegistryMock, sessionManagerMock, deviceAuth, certificateManager);
        assertTrue(api.verifyClientDeviceIdentity(CertificateHelper.toPem(clientCertificateChain)));
    }


}