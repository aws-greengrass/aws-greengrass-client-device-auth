/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.certificateauthority;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.CertificateUpdateEvent;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequest;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.connectivity.CISShadowMonitor;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.helpers.TestHelpers;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.lifecyclemanager.exceptions.ServiceLoadException;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
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
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper.PEM_BOUNDARY_CERTIFICATE;
import static com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper.PEM_BOUNDARY_PRIVATE_KEY;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_CERTIFICATE_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CA_PRIVATE_KEY_URI;
import static com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration.CERTIFICATE_AUTHORITY_TOPIC;
import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CustomCaConfigurationTest {
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
    private IotAuthClientFake iotAuthClientFake;


    @BeforeEach
    void setup(ExtensionContext context) throws DeviceConfigurationException {
        ignoreExceptionOfType(context, SpoolerStoreException.class);

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();

        kernel.getContext().put(GroupManager.class, groupManager);
        kernel.getContext().put(CertificateExpiryMonitor.class, certExpiryMonitorMock);
        kernel.getContext().put(CISShadowMonitor.class, cisShadowMonitorMock);
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);

        iotAuthClientFake = new IotAuthClientFake();
        kernel.getContext().put(IotAuthClient.class, iotAuthClientFake);

        lenient().when(clientFactory.fetchGreengrassV2DataClient()).thenReturn(client);
    }

    @AfterEach
    void cleanup() {
        kernel.shutdown();
    }

    // TODO: Consolidate this test helpers with ClientDevicesAuthServiceTest
    private void givenNucleusRunningWithConfig(String configFileName) throws InterruptedException {
        CountDownLatch authServiceRunning = new CountDownLatch(1);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        kernel.getContext().addGlobalStateChangeListener((service, was, newState) -> {
            if (ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME.equals(service.getName()) && service.getState()
                    .equals(State.RUNNING)) {
                authServiceRunning.countDown();
            }
        });
        kernel.launch();
        assertThat(authServiceRunning.await(30L, TimeUnit.SECONDS), is(true));
    }

    private static Pair<X509Certificate[], PrivateKey> givenRootAndIntermediateCA() throws NoSuchAlgorithmException,
            CertificateException,
            OperatorCreationException, CertIOException {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);

        KeyPair intermediateKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate intermediateCA = CertificateTestHelpers.createIntermediateCertificateAuthority(
                rootCA, "intermediate", intermediateKeyPair.getPublic(), rootKeyPair.getPrivate());

        return new Pair<>(
            new X509Certificate[]{intermediateCA, rootCA},
            intermediateKeyPair.getPrivate()
        );
    }

    /**
     * Simulates an external party configuring their custom CA.
     */
    private void givenCDAWithCustomCertificateAuthority(URI privateKeyUri, URI certificateUri) throws
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

    private Map<String, URI> givenCAStoredOnFilesystem(PrivateKey pk, X509Certificate... chain) throws IOException,
            CertificateEncodingException, URISyntaxException {
        Path pkPath = rootDir.resolve("private.key").toAbsolutePath();
        CertificateTestHelpers.writeToPath(
                pkPath, PEM_BOUNDARY_PRIVATE_KEY, Collections.singletonList(pk.getEncoded()));

        List<byte[]> encodings = new ArrayList<>();
        for (X509Certificate x509Certificate : chain) {
            byte[] encoded = x509Certificate.getEncoded();
            encodings.add(encoded);
        }

        Path chainPath = rootDir.resolve("certificate.pem").toAbsolutePath();
        CertificateTestHelpers.writeToPath(
                chainPath.toAbsolutePath(), PEM_BOUNDARY_CERTIFICATE, encodings);

        return new HashMap<String, URI>(){{
            put("privateKey", pkPath.toUri());
            put("certificateAuthority", chainPath.toUri());
        }};
    }

    @Test
    void Given_CustomCAConfiguration_WHEN_issuingAClientCertificate_THEN_itsSignedByCustomCA() throws
            CertificateException, URISyntaxException, CertificateGenerationException, ExecutionException,
            InterruptedException, TimeoutException, ServiceLoadException, NoSuchAlgorithmException,
            OperatorCreationException, IOException {
        Pair<X509Certificate[], PrivateKey> credentials = givenRootAndIntermediateCA();
        Map<String, URI> uris = givenCAStoredOnFilesystem(credentials.getRight(), credentials.getLeft());

        AtomicReference<CertificateUpdateEvent> eventRef = new AtomicReference<>();
        Pair<CompletableFuture<Void>, Consumer<CertificateUpdateEvent>> asyncCall =
                TestUtils.asyncAssertOnConsumer(eventRef::set, 2);
        GetCertificateRequest request = buildCertificateUpdateRequest(
                GetCertificateRequestOptions.CertificateType.CLIENT, asyncCall.getRight());

        givenNucleusRunningWithConfig("config.yaml");
        subscribeToCertificateUpdates(request);
        givenCDAWithCustomCertificateAuthority(uris.get("privateKey"), uris.get("certificateAuthority"));

        TestHelpers.eventuallyTrue(() -> {
            try {
                X509Certificate issuedClientCertificate = eventRef.get().getCertificate();
                X509Certificate intermediateCA = credentials.getLeft()[0];
                return CertificateTestHelpers.wasCertificateIssuedBy(intermediateCA, issuedClientCertificate);
            } catch (CertificateException e) {
                return  false;
            }
        });
    }

    @Test
    void GIVEN_CustomCAConfiguration_WHEN_whenGeneratingClientCerts_THEN_GGComponentIsVerified() throws
            Exception {
        Pair<X509Certificate[], PrivateKey> credentials = givenRootAndIntermediateCA();
        Map<String, URI> uris = givenCAStoredOnFilesystem(credentials.getRight(), credentials.getLeft());

        AtomicReference<CertificateUpdateEvent> eventRef = new AtomicReference<>();
        GetCertificateRequest request = buildCertificateUpdateRequest(
                GetCertificateRequestOptions.CertificateType.CLIENT, eventRef::set);

        givenNucleusRunningWithConfig("config.yaml");
        subscribeToCertificateUpdates(request);
        givenCDAWithCustomCertificateAuthority(uris.get("privateKey"), uris.get("certificateAuthority"));


        TestHelpers.eventuallyTrue(() -> {
            try {
                X509Certificate intermediateCA =  credentials.getLeft()[0];
                X509Certificate issuedClientCertificate = eventRef.get().getCertificate();
                return CertificateTestHelpers.wasCertificateIssuedBy(intermediateCA, issuedClientCertificate);
            } catch (CertificateException e) {
                return  false;
            }
        });

        CertificateUpdateEvent event = eventRef.get();
        X509Certificate[] clientChain = ArrayUtils.addAll(
                new X509Certificate[]{event.getCertificate()},
                event.getCaCertificates()
        );
        ClientDevicesAuthServiceApi api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);
        String pem = CertificateHelper.toPem(clientChain);
        assertTrue(
            api.verifyClientDeviceIdentity(pem),
            String.format("certificate is not a greengrass component certificate %s", pem)
        );
    }

    @Test
    void GIVEN_customCAConfigurationWithACAChain_WHEN_registeringCAWithIotCore_THEN_highestTrustCAUploaded() throws
        Exception {
        Pair<X509Certificate[], PrivateKey> credentials = givenRootAndIntermediateCA();
        Map<String, URI> uris = givenCAStoredOnFilesystem( credentials.getRight(), credentials.getLeft());

        givenNucleusRunningWithConfig("config.yaml");
        givenCDAWithCustomCertificateAuthority(uris.get("privateKey"), uris.get("certificateAuthority"));

        ArgumentCaptor<PutCertificateAuthoritiesRequest> requestCaptor =
                ArgumentCaptor.forClass(PutCertificateAuthoritiesRequest.class);
        verify(client, atLeastOnce()).putCertificateAuthorities(requestCaptor.capture());

        X509Certificate[] chain = credentials.getLeft();
        X509Certificate rootCa = chain[chain.length - 1];
        List<String> expectedPem = Collections.singletonList(CertificateHelper.toPem(rootCa));
        TestHelpers.eventuallyTrue(() -> {
            PutCertificateAuthoritiesRequest lastRequest = requestCaptor.getValue();
            return lastRequest.coreDeviceCertificates().equals(expectedPem);
        });
    }

    @Test
    void GIVEN_managedCAConfiguration_WHEN_updatedToCustomCAConfiguration_THEN_serverCertificatesAreRotated() throws
            Exception{
        Pair<X509Certificate[], PrivateKey> credentials = givenRootAndIntermediateCA();
        Map<String, URI> uris = givenCAStoredOnFilesystem(credentials.getRight(), credentials.getLeft());

        AtomicReference<CertificateUpdateEvent> eventRef = new AtomicReference<>();
        Pair<CompletableFuture<Void>, Consumer<CertificateUpdateEvent>> asyncCall =
                TestUtils.asyncAssertOnConsumer(eventRef::set, 2);
        GetCertificateRequest request = buildCertificateUpdateRequest(
                GetCertificateRequestOptions.CertificateType.SERVER, asyncCall.getRight());

        givenNucleusRunningWithConfig("config.yaml");
        subscribeToCertificateUpdates(request);
        givenCDAWithCustomCertificateAuthority(uris.get("privateKey"), uris.get("certificateAuthority"));

        TestHelpers.eventuallyTrue(() -> {
            try {
                X509Certificate intermediateCA =  credentials.getLeft()[0];
                X509Certificate issuedClientCertificate = eventRef.get().getCertificate();
                return CertificateTestHelpers.wasCertificateIssuedBy(intermediateCA, issuedClientCertificate);
            } catch (CertificateException e) {
                return  false;
            }
        });
    }
}