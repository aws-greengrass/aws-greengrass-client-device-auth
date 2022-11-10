/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.certificate.handlers.CertificateRotationHandler;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.certificate.CertificatesConfig;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.connectivity.CISShadowMonitor;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.TestUtils;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.Pair;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_CERTIFICATE_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_PRIVATE_KEY_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CERTIFICATE_AUTHORITY_TOPIC;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CertificateManagerTest {
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
    CertificateRotationHandler certRotationMonitor;


    @BeforeEach
    void beforeEach() {
        DomainEvents domainEvents = new DomainEvents();
        certificateStore = spy(new CertificateStore(tmpPath, domainEvents));
        certRotationMonitor = new CertificateRotationHandler(mockConnectivityInformation, domainEvents);

        certificateManager =
                new CertificateManager(certificateStore, mockConnectivityInformation, mockCertExpiryMonitor,
                        mockShadowMonitor, Clock.systemUTC(), clientFactoryMock, securityServiceMock,
                        certRotationMonitor);

        CertificatesConfig certificatesConfig =
                new CertificatesConfig(Topics.of(new Context(), CONFIGURATION_CONFIG_KEY, null));
        certificateManager.updateCertificatesConfiguration(certificatesConfig);
    }

    @AfterEach
    void afterEach() {
        reset(securityServiceMock);
    }

    @Test
    void GIVEN_customCAConfiguration_WHEN_configureCustomCA_THEN_returnsCustomCA() throws Exception {
        // Given
        certificateManager.generateCA("", CertificateStore.CAType.RSA_2048);

        Instant now = Instant.now();
        KeyPair keyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate caCertificate =
                CertificateHelper.createCACertificate(keyPair, Date.from(now), Date.from(now.plusSeconds(10)),
                        "Custom Core CA", CertificateHelper.ProviderType.DEFAULT);

        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");

        Topics configurationTopics = Topics.of(new Context(), CLIENT_DEVICES_AUTH_SERVICE_NAME, null);
        configurationTopics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_PRIVATE_KEY_URI)
                .withValue(privateKeyUri.toString());
        configurationTopics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_CERTIFICATE_URI)
                .withValue(certificateUri.toString());

        CDAConfiguration cdaConfiguration = CDAConfiguration.from(configurationTopics);

        // TODO: Write the actual certificate to the file system and avoid mocking the security service. Doing
        //  this is a bad given we are exposing implementation details on the test.
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(keyPair);
        doReturn(new X509Certificate[]{caCertificate}).when(certificateStore)
                .loadCaCertificateChain(certificateUri);

        // When
        certificateManager.configureCustomCA(cdaConfiguration.getCertificateAuthorityConfiguration());

        // Then
        List<String> caPemStrings = certificateManager.getCACertificates();
        String caPem = caPemStrings.get(0);
        assertEquals(caPem, CertificateHelper.toPem(caCertificate));
    }

    @Test
    void Given_defaultCAConfiguration_THEN_returnsAutoGeneratedCA() throws Exception {
        // Given
        certificateManager.generateCA("", CertificateStore.CAType.RSA_2048);

        Instant now = Instant.now();
        KeyPair keyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate caCertificate =
                CertificateHelper.createCACertificate(keyPair, Date.from(now), Date.from(now.plusSeconds(10)),
                        "Custom Core CA", CertificateHelper.ProviderType.DEFAULT);

        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");

        // TODO: Write the actual certificate to the file system and avoid mocking the security service. Doing
        //  this is a bad given we are exposing implementation details on the test.
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(keyPair);

        // Then
        List<String> caPemStrings = certificateManager.getCACertificates();
        String caPem = caPemStrings.get(0);
        assertNotEquals(caPem, CertificateHelper.toPem(caCertificate));
    }

    @Test
    void GIVEN_defaultCertManager_WHEN_getCACertificates_THEN_singleCAReturned()
            throws CertificateEncodingException, KeyStoreException, IOException {
        certificateManager.generateCA("", CertificateStore.CAType.RSA_2048);

        List<String> caPemList = certificateManager.getCACertificates();
        assertEquals(1, caPemList.size(), "expected single CA certificate");
    }

    @Test
    void GIVEN_defaultCertManager_WHEN_subscribeToCertificateUpdates_THEN_certificateReceived()
            throws InterruptedException, ExecutionException, TimeoutException, CertificateGenerationException,
            KeyStoreException {
        certificateManager.generateCA("", CertificateStore.CAType.RSA_2048);

        Pair<CompletableFuture<Void>, Consumer<CertificateUpdateEvent>> con = TestUtils.asyncAssertOnConsumer((a) -> {
        }, 3);

        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.SERVER);
        GetCertificateRequest certificateRequest =
                new GetCertificateRequest("testService", requestOptions, con.getRight());

        // Subscribe multiple times to show that a new certificate is generated on each call
        certificateManager.subscribeToCertificateUpdates(certificateRequest);
        certificateManager.subscribeToCertificateUpdates(certificateRequest);
        certificateManager.subscribeToCertificateUpdates(certificateRequest);
        con.getLeft().get(1, TimeUnit.SECONDS);
    }

    @Test
    void GIVEN_caIsNotReady_WHEN_subscribeToCertificateUpdates_THEN_receivesCertsWhenCAReady()
            throws CertificateGenerationException, KeyStoreException {
        AtomicInteger callCount = new AtomicInteger(0);
        Consumer<CertificateUpdateEvent> consumer = (a) -> {
            callCount.getAndIncrement();
        };
        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.SERVER);
        GetCertificateRequest certificateRequest = new GetCertificateRequest("testService", requestOptions, consumer);
        certRotationMonitor.listen();

        // Ca has not been configured
        certificateManager.subscribeToCertificateUpdates(certificateRequest);
        assertEquals(callCount.get(), 0);

        // Configure CA
        certificateManager.generateCA("", CertificateStore.CAType.RSA_2048);
        assertEquals(callCount.get(), 1);
    }

    @Test
    void GIVEN_serverCertRequest_WHEN_serverCertificateIsGenerated_THEN_canSuccessfullyImportToKeyStore()
            throws CertificateGenerationException, KeyStoreException {
        certificateManager.generateCA("", CertificateStore.CAType.RSA_2048);

        Consumer<CertificateUpdateEvent> cb = t -> {
            try {
                X509Certificate[] certChain =
                        Stream.concat(Stream.of(t.getCertificate()), Arrays.stream(t.getCaCertificates()))
                                .toArray(X509Certificate[]::new);
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null, null);
                ks.setKeyEntry("key", t.getKeyPair().getPrivate(), "".toCharArray(), certChain);
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
                Assertions.fail(e);
            }
        };

        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.SERVER);
        GetCertificateRequest certificateRequest = new GetCertificateRequest("testService", requestOptions, cb);

        certificateManager.subscribeToCertificateUpdates(certificateRequest);
    }

    @Test
    void GIVEN_clientCertRequest_WHEN_clientCertificateIsGenerated_THEN_canSuccessfullyImportToKeyStore()
            throws CertificateGenerationException, KeyStoreException {
        certificateManager.generateCA("", CertificateStore.CAType.RSA_2048);

        Consumer<CertificateUpdateEvent> cb = t -> {
            try {
                X509Certificate[] certChain =
                        Stream.concat(Stream.of(t.getCertificate()), Arrays.stream(t.getCaCertificates()))
                                .toArray(X509Certificate[]::new);
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null, null);
                ks.setKeyEntry("key", t.getKeyPair().getPrivate(), "".toCharArray(), certChain);
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
                Assertions.fail(e);
            }
        };

        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.CLIENT);
        GetCertificateRequest certificateRequest = new GetCertificateRequest("testService", requestOptions, cb);

        certificateManager.subscribeToCertificateUpdates(certificateRequest);
    }

    @Test
    void GIVEN_caChain_WHEN_caChainChanges_THEN_subscribersGetLatestValues()
            throws NoSuchAlgorithmException, CertificateException, OperatorCreationException, IOException,
            CertificateGenerationException, KeyStoreException, ExecutionException, InterruptedException,
            TimeoutException {
        certificateManager.generateCA("", CertificateStore.CAType.RSA_2048);
        AtomicReference<CertificateUpdateEvent> eventRef = new AtomicReference<>();

        Pair<CompletableFuture<Void>, Consumer<CertificateUpdateEvent>> asyncCall =
                TestUtils.asyncAssertOnConsumer(eventRef::set, 1);
        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(GetCertificateRequestOptions.CertificateType.CLIENT);
        GetCertificateRequest request =
                new GetCertificateRequest("com.aws.clients.Plugin", requestOptions, asyncCall.getRight());

        KeyPair caAKeys = CertificateStore.newRSAKeyPair(2048);
        X509Certificate caA = CertificateTestHelpers.createRootCertificateAuthority("Root A", caAKeys);

        KeyPair caBKeys = CertificateStore.newRSAKeyPair(2048);
        X509Certificate caB = CertificateTestHelpers.createRootCertificateAuthority("Root B", caBKeys);


        certificateStore.setCaKeyAndCertificateChain(CertificateHelper.ProviderType.DEFAULT, caAKeys.getPrivate(), caA);
        certificateManager.subscribeToCertificateUpdates(request);

        asyncCall.getLeft().get(2, TimeUnit.SECONDS);
        assertEquals(1, eventRef.get().getCaCertificates().length);
        assertEquals(CertificateHelper.toPem(caA), CertificateHelper.toPem(eventRef.get().getCaCertificates()[0]));

        ArgumentCaptor<CertificateGenerator> generator = ArgumentCaptor.forClass(CertificateGenerator.class);
        verify(mockCertExpiryMonitor).addToMonitor(generator.capture());

        certificateStore.setCaKeyAndCertificateChain(CertificateHelper.ProviderType.DEFAULT, caBKeys.getPrivate(), caB);

        // This part below just simulates the expiry monitor triggering expired certificates after the ca had changed
        generator.getValue().generateCertificate(ArrayList::new, "testing");
        assertEquals(1, eventRef.get().getCaCertificates().length);
        assertEquals(CertificateHelper.toPem(caB), CertificateHelper.toPem(eventRef.get().getCaCertificates()[0]));
    }


    @Test
    void GIVEN_nullRequest_WHEN_subscribeToCertificateUpdates_THEN_throwsNPE() throws KeyStoreException {
        certificateManager.generateCA("", CertificateStore.CAType.RSA_2048);
        Assertions.assertThrows(NullPointerException.class,
                () -> certificateManager.subscribeToCertificateUpdates(null));
    }
}