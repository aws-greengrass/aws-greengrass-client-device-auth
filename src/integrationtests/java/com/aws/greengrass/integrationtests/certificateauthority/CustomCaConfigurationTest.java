/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.certificateauthority;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.connectivity.CISShadowMonitor;
import com.aws.greengrass.clientdevices.auth.exception.CertificateChainLoadingException;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.lifecyclemanager.exceptions.ServiceLoadException;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.security.exceptions.KeyLoadingException;
import com.aws.greengrass.security.exceptions.ServiceUnavailableException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.TestUtils;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.Pair;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.PutCertificateAuthoritiesRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_CERTIFICATE_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_PRIVATE_KEY_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CERTIFICATE_AUTHORITY_TOPIC;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CustomCaConfigurationTest {
    @Mock
    SecurityService securityServiceMock;
    @Mock
    private GreengrassServiceClientFactory clientFactory;
    @Mock
    private GroupManager groupManager;
    @Mock
    CertificateExpiryMonitor certExpiryMonitorMock;
    @Mock
    CISShadowMonitor cisShadowMonitorMock;
    @Mock
    private GreengrassV2DataClient client;
    @TempDir
    Path rootDir;
    private Kernel kernel;
    private CertificateStore certificateStoreSpy;



    @BeforeEach
    void setup(ExtensionContext context) throws DeviceConfigurationException {
        ignoreExceptionOfType(context, SpoolerStoreException.class);

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        kernel.getContext().put(GroupManager.class, groupManager);
        kernel.getContext().put(SecurityService.class, securityServiceMock);
        kernel.getContext().put(CertificateExpiryMonitor.class, certExpiryMonitorMock);
        kernel.getContext().put(CISShadowMonitor.class, cisShadowMonitorMock);
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);

        DomainEvents domainEvents =  new DomainEvents();
        certificateStoreSpy = spy(new CertificateStore(rootDir, domainEvents , securityServiceMock));

        kernel.getContext().put(DomainEvents.class, domainEvents);
        kernel.getContext().put(CertificateStore.class, certificateStoreSpy);

        lenient().when(clientFactory.fetchGreengrassV2DataClient()).thenReturn(client);
    }

    @AfterEach
    void cleanup() {
        kernel.shutdown();
    }

    private void givenNucleusRunningWithConfig(String configFileName, Consumer<State> consumer) throws InterruptedException {
        CountDownLatch authServiceRunning = new CountDownLatch(1);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        kernel.getContext().addGlobalStateChangeListener((service, was, newState) -> {
            if (ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME.equals(service.getName())) {
                State serviceState = service.getState();
                consumer.accept(serviceState);

                if (serviceState.equals(State.RUNNING)) {
                    authServiceRunning.countDown();
                }

            }
        });
        kernel.launch();

        assertThat(authServiceRunning.await(30L, TimeUnit.SECONDS), is(true));
    }

    private void givenNucleusRunningWithConfig(String configFileName) throws InterruptedException {
        givenNucleusRunningWithConfig(configFileName, (State s) -> {});
    }


    private static Pair<X509Certificate[], KeyPair[]> givenRootAndIntermediateCA() throws NoSuchAlgorithmException,
            CertificateException,
            OperatorCreationException, CertIOException {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);

        KeyPair intermediateKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate intermediateCA = CertificateTestHelpers.createIntermediateCertificateAuthority(
                rootCA, "intermediate", intermediateKeyPair.getPublic(), rootKeyPair.getPrivate());

        return new Pair<>(
                new X509Certificate[]{intermediateCA, rootCA},
                new KeyPair[]{intermediateKeyPair, rootKeyPair}
        );
    }

    /**
     * Simulates an external party configuring their custom CA.
     */
    private void configureCDAWithCustomCertificateAuthority(URI privateKeyUri, URI certificateUri) throws
            ServiceLoadException {
        // Service Configuration
        Topics topics = kernel.locate(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME).getConfig();
        topics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_PRIVATE_KEY_URI)
                .withValue(privateKeyUri.toString());
        topics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_CERTIFICATE_URI)
                .withValue(certificateUri.toString());

        kernel.getContext().waitForPublishQueueToClear();
    }

    private GetCertificateRequest buildCertificateUpdateRequest(
            GetCertificateRequestOptions.CertificateType type, Consumer<CertificateUpdateEvent> cb) {
        GetCertificateRequestOptions requestOptions = new GetCertificateRequestOptions();
        requestOptions.setCertificateType(type);
        return new GetCertificateRequest("com.aws.clients.Plugin", requestOptions, cb);
    }

    /**
     * Simulates the subscription client or server component create to get their certificates.
     */
    private void subscribeToCertificateUpdates(GetCertificateRequest request)
            throws CertificateGenerationException {
        ClientDevicesAuthServiceApi api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);
        api.subscribeToCertificateUpdates(request);
    }

    @Test
    void Given_CustomCAConfiguration_WHEN_issuingAClientCertificate_THEN_itsSignedByCustomCA() throws
            CertificateException, URISyntaxException, CertificateGenerationException, ExecutionException,
            InterruptedException, TimeoutException, ServiceLoadException, NoSuchAlgorithmException,
            OperatorCreationException, IOException, KeyLoadingException, ServiceUnavailableException,
            CertificateChainLoadingException {
        Pair<X509Certificate[], KeyPair[]> credentials = givenRootAndIntermediateCA();
        X509Certificate[] chain = credentials.getLeft();
        X509Certificate intermediateCA = chain[0];
        KeyPair[] certificateKeys = credentials.getRight();
        KeyPair intermediateKeyPair = certificateKeys[0];

        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(intermediateKeyPair);
        doReturn(chain).when(certificateStoreSpy).loadCaCertificateChain(privateKeyUri, certificateUri);

        AtomicReference<CertificateUpdateEvent> eventRef = new AtomicReference<>();
        Pair<CompletableFuture<Void>, Consumer<CertificateUpdateEvent>> asyncCall =
                TestUtils.asyncAssertOnConsumer(eventRef::set, 1);
        GetCertificateRequest request = buildCertificateUpdateRequest(
                GetCertificateRequestOptions.CertificateType.CLIENT, asyncCall.getRight());

        givenNucleusRunningWithConfig("config.yaml");
        configureCDAWithCustomCertificateAuthority(privateKeyUri, certificateUri);
        subscribeToCertificateUpdates(request);

        asyncCall.getLeft().get(1, TimeUnit.SECONDS);
        assertTrue(CertificateTestHelpers.wasCertificateIssuedBy(intermediateCA, eventRef.get().getCertificate()));
    }

    @Test
    void GIVEN_CustomCAConfiguration_WHEN_whenGeneratingClientCerts_THEN_GGComponentIsVerified() throws
            NoSuchAlgorithmException, CertificateException, OperatorCreationException, IOException,
            URISyntaxException, KeyLoadingException, ServiceUnavailableException,
            CertificateChainLoadingException, CertificateGenerationException, ExecutionException, InterruptedException,
            TimeoutException, ServiceLoadException {
        Pair<X509Certificate[], KeyPair[]> credentials = givenRootAndIntermediateCA();
        X509Certificate[] chain = credentials.getLeft();
        KeyPair[] certificateKeys = credentials.getRight();
        KeyPair intermediateKeyPair = certificateKeys[0];

        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(intermediateKeyPair);
        doReturn(chain).when(certificateStoreSpy).loadCaCertificateChain(privateKeyUri, certificateUri);

        AtomicReference<CertificateUpdateEvent> eventRef = new AtomicReference<>();
        Pair<CompletableFuture<Void>, Consumer<CertificateUpdateEvent>> asyncCall =
                TestUtils.asyncAssertOnConsumer(eventRef::set, 1);
        GetCertificateRequest request = buildCertificateUpdateRequest(
                GetCertificateRequestOptions.CertificateType.CLIENT, asyncCall.getRight());

        givenNucleusRunningWithConfig("config.yaml");
        configureCDAWithCustomCertificateAuthority(privateKeyUri, certificateUri);
        subscribeToCertificateUpdates(request);

        asyncCall.getLeft().get(1, TimeUnit.SECONDS);
        CertificateUpdateEvent event = eventRef.get();
        X509Certificate[] clientChain = ArrayUtils.addAll(
                new X509Certificate[]{event.getCertificate()},
                event.getCaCertificates()
        );
        ClientDevicesAuthServiceApi api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);
        assertTrue(api.verifyClientDeviceIdentity(CertificateHelper.toPem(clientChain)));
    }

    @Test
    void GIVEN_customCAConfigurationWithACAChain_WHEN_registeringCAWithIotCore_THEN_highestTrustCAUploaded() throws
            CertificateChainLoadingException, KeyLoadingException, CertificateException, NoSuchAlgorithmException,
            URISyntaxException, ServiceUnavailableException, OperatorCreationException, IOException,
            ServiceLoadException, InterruptedException {
        Pair<X509Certificate[], KeyPair[]> credentials = givenRootAndIntermediateCA();
        X509Certificate[] chain = credentials.getLeft();
        KeyPair[] certificateKeys = credentials.getRight();
        KeyPair intermediateKeyPair = certificateKeys[0];

        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(intermediateKeyPair);
        doReturn(chain).when(certificateStoreSpy).loadCaCertificateChain(privateKeyUri, certificateUri);

        givenNucleusRunningWithConfig("config.yaml");
        configureCDAWithCustomCertificateAuthority(privateKeyUri, certificateUri);

        ArgumentCaptor<PutCertificateAuthoritiesRequest> requestCaptor =
                ArgumentCaptor.forClass(PutCertificateAuthoritiesRequest.class);
        verify(client, atLeastOnce()).putCertificateAuthorities(requestCaptor.capture());

        List<String> expectedPem = Collections.singletonList(CertificateHelper.toPem(chain[chain.length - 1]));
        PutCertificateAuthoritiesRequest lastRequest = requestCaptor.getValue();
        assertEquals(lastRequest.coreDeviceCertificates(), expectedPem);
    }

    @Test
    void GIVEN_managedCAConfiguration_WHEN_updatedToCustomCAConfiguration_THEN_serverCertificatesAreRotated() throws
            InterruptedException, CertificateGenerationException, CertificateException, NoSuchAlgorithmException,
            OperatorCreationException, IOException, URISyntaxException, KeyLoadingException,
            ServiceUnavailableException, CertificateChainLoadingException, ServiceLoadException, ExecutionException,
            TimeoutException {
        Pair<X509Certificate[], KeyPair[]> credentials = givenRootAndIntermediateCA();
        X509Certificate[] chain = credentials.getLeft();
        X509Certificate intermediateCA =  chain[0];
        KeyPair[] certificateKeys = credentials.getRight();
        KeyPair intermediateKeyPair = certificateKeys[0];

        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");

        AtomicReference<CertificateUpdateEvent> eventRef = new AtomicReference<>();
        Pair<CompletableFuture<Void>, Consumer<CertificateUpdateEvent>> asyncCall =
                TestUtils.asyncAssertOnConsumer(eventRef::set, 2);
        GetCertificateRequest request = buildCertificateUpdateRequest(
                GetCertificateRequestOptions.CertificateType.SERVER, asyncCall.getRight());

        givenNucleusRunningWithConfig("config.yaml");
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(intermediateKeyPair);
        doReturn(chain).when(certificateStoreSpy).loadCaCertificateChain(privateKeyUri, certificateUri);

        subscribeToCertificateUpdates(request);
        configureCDAWithCustomCertificateAuthority(privateKeyUri, certificateUri);

        // Called 2 times. 1 for initial manages CA and then after the config is changes to use custom CA
        asyncCall.getLeft().get(2, TimeUnit.SECONDS);
        CertificateUpdateEvent event = eventRef.get();
        assertTrue(CertificateTestHelpers.wasCertificateIssuedBy(intermediateCA, event.getCertificate()));
    }

    @Test
    void GIVEN_invalidConfigServiceErrored_WHEN_whenCorrected_THEN_serviceCanRecover(ExtensionContext context)
            throws CertificateException, NoSuchAlgorithmException, OperatorCreationException, CertIOException,
            URISyntaxException, InterruptedException, KeyLoadingException, ServiceUnavailableException,
            CertificateChainLoadingException, ServiceLoadException, KeyStoreException {
        ignoreExceptionOfType(context, InvalidConfigurationException.class);
        Pair<X509Certificate[], KeyPair[]> credentials = givenRootAndIntermediateCA();
        X509Certificate[] chain = credentials.getLeft();
        KeyPair[] certificateKeys = credentials.getRight();
        KeyPair intermediateKeyPair = certificateKeys[0];
        CountDownLatch authServiceErrored = new CountDownLatch(1);
        Consumer<State> serviceStateChangeListener = (State s) -> {
            if (s.equals(State.ERRORED)) {
                authServiceErrored.countDown();
            }
        };

        givenNucleusRunningWithConfig("config.yaml", serviceStateChangeListener);
        configureCDAWithCustomCertificateAuthority(new URI("file:///private.key"), new URI(""));

        // Configure Custom CA with bad values
        verify(certificateStoreSpy, times(1)).setCaKeyAndCertificateChain(any(), any(), any());
        assertThat(authServiceErrored.await(10L, TimeUnit.SECONDS), is(true));

        // Configure with Good values
        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(intermediateKeyPair);
        doReturn(chain).when(certificateStoreSpy).loadCaCertificateChain(privateKeyUri, certificateUri);
        configureCDAWithCustomCertificateAuthority(privateKeyUri, certificateUri);
        verify(certificateStoreSpy, times(2)).setCaKeyAndCertificateChain(any(), any(), any());
    }

    @Test
    void GIVEN_invalidConfigServiceBroken_WHEN_whenCorrected_THEN_serviceCanRecover(ExtensionContext context)
            throws CertificateException, NoSuchAlgorithmException, OperatorCreationException, CertIOException,
            URISyntaxException, InterruptedException, KeyLoadingException, ServiceUnavailableException,
            CertificateChainLoadingException, ServiceLoadException, KeyStoreException {
        ignoreExceptionOfType(context, InvalidConfigurationException.class);
        ignoreExceptionOfType(context, URISyntaxException.class);
        long waitTimeMsAfterConfigChange = 500;
        Pair<X509Certificate[], KeyPair[]> credentials = givenRootAndIntermediateCA();
        X509Certificate[] chain = credentials.getLeft();
        KeyPair[] certificateKeys = credentials.getRight();
        KeyPair intermediateKeyPair = certificateKeys[0];

        CountDownLatch authServiceBroken = new CountDownLatch(1);
        CountDownLatch recoveredFromBroken = new CountDownLatch(1);
        AtomicBoolean wasBroken = new AtomicBoolean(false);
        Consumer<State> serviceStateChangeListener = (State s) -> {
            if (s.equals(State.BROKEN)) {
                wasBroken.getAndSet(true);
                authServiceBroken.countDown();
            }

            if (wasBroken.get() && s.equals(State.RUNNING)) {
                recoveredFromBroken.countDown();
            }
        };

        givenNucleusRunningWithConfig("config.yaml", serviceStateChangeListener);
        verify(certificateStoreSpy, times(1)).setCaKeyAndCertificateChain(any(), any(), any());

        // Do enough bad operations until the service goes belly up
        configureCDAWithCustomCertificateAuthority(new URI("file:///private.key"), new URI(""));
        Thread.sleep(waitTimeMsAfterConfigChange); // Give enough time for the kernel to cycle through retries and fail
        configureCDAWithCustomCertificateAuthority(new URI(""), new URI("file:///certificate.key"));
        Thread.sleep(waitTimeMsAfterConfigChange);
        configureCDAWithCustomCertificateAuthority(new URI("/private.key"), new URI("file:///certificate.key"));
        Thread.sleep(waitTimeMsAfterConfigChange);
        assertThat(authServiceBroken.await(10L, TimeUnit.SECONDS), is(true));

        //  Do the right thing
        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(intermediateKeyPair);
        doReturn(chain).when(certificateStoreSpy).loadCaCertificateChain(privateKeyUri, certificateUri);
        configureCDAWithCustomCertificateAuthority(privateKeyUri, certificateUri);
        assertThat(recoveredFromBroken.await(10L, TimeUnit.SECONDS), is(true));
    }
}