/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.certificateauthority;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.lifecyclemanager.exceptions.ServiceLoadException;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.security.SecurityService;
import com.aws.greengrass.security.exceptions.CertificateChainLoadingException;
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
import java.nio.file.Paths;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
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
    CertificateExpiryMonitor certExpiryMonitor;
    @Mock
    private GreengrassV2DataClient client;
    @TempDir
    Path rootDir;
    private Kernel kernel;


    @BeforeEach
    void setup(ExtensionContext context) throws DeviceConfigurationException {
        ignoreExceptionOfType(context, SpoolerStoreException.class);

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        kernel.getContext().put(GroupManager.class, groupManager);
        kernel.getContext().put(SecurityService.class, securityServiceMock);
        kernel.getContext().put(CertificateExpiryMonitor.class, certExpiryMonitor);
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);

        lenient().when(clientFactory.fetchGreengrassV2DataClient()).thenReturn(client);
    }

    @AfterEach
    void cleanup() {
        kernel.shutdown();
    }

    // TODO: Consolidate this test helpers with ClientDevicesAuthServiceTest
    private void startNucleus() throws InterruptedException {
        CountDownLatch authServiceRunning = new CountDownLatch(1);
        Path resourceDirectory = Paths.get(
                "src", "test", "resources", "com", "aws", "greengrass", "clientdevices", "auth", "config.yaml");
        Path testPath = rootDir.toAbsolutePath();
        kernel.parseArgs("-r", testPath.toString(), "-i", resourceDirectory.toFile().getAbsolutePath());
        kernel.getContext().addGlobalStateChangeListener((service, was, newState) -> {
            if (ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME.equals(service.getName()) && service.getState()
                    .equals(State.RUNNING)) {
                authServiceRunning.countDown();
            }
        });
        kernel.launch();
        assertThat(authServiceRunning.await(30L, TimeUnit.SECONDS), is(true));
    }

    private static Pair<X509Certificate[], KeyPair[]> arrangeCredentials() throws NoSuchAlgorithmException,
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
    private void arrangeCDAWithCustomCertificateAuthority(URI privateKeyUri, URI certificateUri) throws
            ServiceLoadException {
        // Service Configuration
        Topics topics = kernel.locate(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME).getConfig();
        topics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_PRIVATE_KEY_URI)
                .withValue(privateKeyUri.toString());
        topics.lookup(CONFIGURATION_CONFIG_KEY, CERTIFICATE_AUTHORITY_TOPIC, CA_CERTIFICATE_URI)
                .withValue(certificateUri.toString());

        kernel.getContext().waitForPublishQueueToClear();
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

        CertificateManager manager = kernel.getContext().get(CertificateManager.class);
        manager.subscribeToCertificateUpdates(certificateRequest);
        asyncCall.getLeft().get(1, TimeUnit.SECONDS);

        // Arrange the certificate chain leaf comes first, root comes last
        return ArrayUtils.addAll(new X509Certificate[]{eventRef.get().getCertificate()},
                eventRef.get().getCaCertificates());
    }


    @Test
    void Given_CustomCAConfiguration_WHEN_issuingAClientCertificate_THEN_itsSignedByCustomCA() throws
            CertificateException, URISyntaxException, CertificateGenerationException, ExecutionException,
            InterruptedException, TimeoutException, ServiceLoadException, NoSuchAlgorithmException,
            OperatorCreationException, CertIOException, KeyLoadingException, ServiceUnavailableException,
            CertificateChainLoadingException {
        Pair<X509Certificate[], KeyPair[]> credentials = arrangeCredentials();
        X509Certificate[] chain = credentials.getLeft();
        KeyPair[] certificateKeys = credentials.getRight();
        KeyPair intermediateKeyPair = certificateKeys[0];

        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(intermediateKeyPair);
        when(securityServiceMock.getCertificateChain(privateKeyUri, certificateUri)).thenReturn(chain);

        startNucleus();
        arrangeCDAWithCustomCertificateAuthority(privateKeyUri, certificateUri);

        X509Certificate[] clientCertificateChain = arrangeClientComponentCertificateChain();
        assertTrue(CertificateTestHelpers.wasCertificateIssuedBy(chain[0], clientCertificateChain[0]));
    }

    @Test
    void GIVEN_CustomCAConfiguration_WHEN_whenGeneratingClientCerts_THEN_GGComponentIsVerified() throws
            NoSuchAlgorithmException, CertificateException, OperatorCreationException, IOException,
            URISyntaxException, KeyLoadingException, ServiceUnavailableException,
            CertificateChainLoadingException, CertificateGenerationException, ExecutionException, InterruptedException,
            TimeoutException, ServiceLoadException {
        Pair<X509Certificate[], KeyPair[]> credentials = arrangeCredentials();
        X509Certificate[] chain = credentials.getLeft();
        KeyPair[] certificateKeys = credentials.getRight();
        KeyPair intermediateKeyPair = certificateKeys[0];

        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(intermediateKeyPair);
        when(securityServiceMock.getCertificateChain(privateKeyUri, certificateUri)).thenReturn(chain);

        startNucleus();
        arrangeCDAWithCustomCertificateAuthority(privateKeyUri, certificateUri);

        X509Certificate[] clientCertChain = arrangeClientComponentCertificateChain();
        ClientDevicesAuthServiceApi api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);
        assertTrue(api.verifyClientDeviceIdentity(CertificateHelper.toPem(clientCertChain)));
    }

    @Test
    void GIVEN_customCAConfigurationWithACAChain_WHEN_registeringCAWithIotCore_THEN_highestTrustCAUploaded() throws
            CertificateChainLoadingException, KeyLoadingException, CertificateException, NoSuchAlgorithmException,
            URISyntaxException, ServiceUnavailableException, OperatorCreationException, IOException,
            ServiceLoadException, InterruptedException, DeviceConfigurationException, KeyStoreException {
        Pair<X509Certificate[], KeyPair[]> credentials = arrangeCredentials();
        X509Certificate[] chain = credentials.getLeft();
        KeyPair[] certificateKeys = credentials.getRight();
        KeyPair intermediateKeyPair = certificateKeys[0];

        URI privateKeyUri = new URI("file:///private.key");
        URI certificateUri = new URI("file:///certificate.pem");
        when(securityServiceMock.getKeyPair(privateKeyUri, certificateUri)).thenReturn(intermediateKeyPair);
        when(securityServiceMock.getCertificateChain(privateKeyUri, certificateUri)).thenReturn(chain);

        startNucleus();
        arrangeCDAWithCustomCertificateAuthority(privateKeyUri, certificateUri);

        ArgumentCaptor<PutCertificateAuthoritiesRequest> requestCaptor =
                ArgumentCaptor.forClass(PutCertificateAuthoritiesRequest.class);
        verify(client, atLeastOnce()).putCertificateAuthorities(requestCaptor.capture());

        List<String> expectedPem = Collections.singletonList(CertificateHelper.toPem(chain[chain.length - 1]));
        PutCertificateAuthoritiesRequest lastRequest = requestCaptor.getValue();
        assertEquals(lastRequest.coreDeviceCertificates(), expectedPem);
    }

}