/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.ipc;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.lifecyclemanager.GlobalStateChangeListener;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.impl.config.LogConfig;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.UniqueRootPathExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.model.ClientDeviceCredential;
import software.amazon.awssdk.aws.greengrass.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.aws.greengrass.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
class VerifyClientDeviceIdentityTest {
    private static GlobalStateChangeListener listener;
    @TempDir
    Path rootDir;
    private Kernel kernel;
    @Mock
    private GreengrassServiceClientFactory clientFactory;
    @Mock
    private GreengrassV2DataClient client;
    @Mock
    private IotAuthClient iotAuthClient;
    private Certificate clientCertificate;
    private static X509Certificate validClientX509Certificate;
    private static String validClientCertificatePem;

    @BeforeAll
    static void beforeAll()
            throws CertificateException, NoSuchAlgorithmException, OperatorCreationException, IOException {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        KeyPair clientKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);

        validClientX509Certificate =
                CertificateTestHelpers.createClientCertificate(rootCA, "Client", clientKeyPair.getPublic(),
                        rootKeyPair.getPrivate());
        validClientCertificatePem = CertificateHelper.toPem(validClientX509Certificate);
    }

    @BeforeEach
    void beforeEach(ExtensionContext context) throws DeviceConfigurationException, InvalidCertificateException {
        ignoreExceptionOfType(context, SpoolerStoreException.class);
        ignoreExceptionOfType(context, NoSuchFileException.class); // Loading CA keystore

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);

        // TODO: getGreengrassV2DataClient mock can be removed once IotAuthClient migrates to new API
        lenient().when(clientFactory.getGreengrassV2DataClient()).thenReturn(client);
        when(clientFactory.fetchGreengrassV2DataClient()).thenReturn(client);

        // Re-instantiate certs
        clientCertificate = Certificate.fromPem(validClientCertificatePem);
    }

    @AfterEach
    void afterEach() {
        LogConfig.getRootLogConfig().reset();
        kernel.shutdown();
    }

    private void startNucleusWithConfig(String configFileName) throws InterruptedException {
        startNucleusWithConfig(configFileName, State.RUNNING);
    }

    private void startNucleusWithConfig(String configFileName, State expectedServiceState) throws InterruptedException {
        CountDownLatch authServiceRunning = new CountDownLatch(1);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        listener = (GreengrassService service, State was, State newState) -> {
            if (service.getName().equals(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME)
                    && service.getState().equals(expectedServiceState)) {
                authServiceRunning.countDown();
            }
        };
        kernel.getContext().addGlobalStateChangeListener(listener);
        kernel.launch();
        assertThat(authServiceRunning.await(30L, TimeUnit.SECONDS), is(true));
        kernel.getContext().removeGlobalStateChangeListener(listener);
    }

    @Test
    void GIVEN_requestWithValidCertificate_WHEN_verifyClientIdentity_THEN_returnValid() throws Exception {
        kernel.getContext().put(IotAuthClient.class, iotAuthClient);
        clientCertificate.setStatus(Certificate.Status.ACTIVE);
        when(iotAuthClient.getIotCertificate(validClientCertificatePem)).thenReturn(Optional.of(clientCertificate));
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);

            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest().withCredential(
                    new ClientDeviceCredential().withClientDeviceCertificate(validClientCertificatePem));

            CompletableFuture<VerifyClientDeviceIdentityResponse> response =
                    ipcClient.verifyClientDeviceIdentity(request, Optional.empty()).getResponse();
            assertTrue(response.get(10, TimeUnit.SECONDS).isIsValidClientDevice());
        }
    }

    @Test
    void GIVEN_requestWithInvalidPem_WHEN_verifyClientIdentity_THEN_returnNotValid(ExtensionContext context)
            throws Exception {
        kernel.getContext().put(IotAuthClient.class, iotAuthClient);
        ignoreExceptionOfType(context, InvalidCertificateException.class);
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);

            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest().withCredential(
                    new ClientDeviceCredential().withClientDeviceCertificate("invalid pem"));

            CompletableFuture<VerifyClientDeviceIdentityResponse> response =
                    ipcClient.verifyClientDeviceIdentity(request, Optional.empty()).getResponse();
            assertFalse(response.get(10, TimeUnit.SECONDS).isIsValidClientDevice());
        }
    }

    @Test
    void GIVEN_unauthorizedClient_WHEN_verifyClientIdentity_THEN_throwsUnauthorizedError()
            throws ExecutionException, InterruptedException {
        kernel.getContext().put(IotAuthClient.class, iotAuthClient);
        startNucleusWithConfig("BrokerNotAuthorized.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithNoConfig")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest().withCredential(
                    new ClientDeviceCredential().withClientDeviceCertificate("abc"));

            Assertions.assertThrows(Exception.class,
                    () -> ipcClient.verifyClientDeviceIdentity(request, Optional.empty()).getResponse().get());
        }
    }


    @Test
    void GIVEN_inactiveClientCertificate_WHEN_verifyClientIdentity_THEN_returnsNotValid(ExtensionContext context)
            throws Exception {
        startNucleusWithConfig("cda.yaml");
        when(clientFactory.fetchGreengrassV2DataClient().verifyClientDeviceIdentity(
                any(software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityRequest.class)))
                .thenThrow(ValidationException.class);
        ignoreExceptionOfType(context, ValidationException.class);
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest().withCredential(
                    new ClientDeviceCredential().withClientDeviceCertificate(validClientCertificatePem));

            CompletableFuture<VerifyClientDeviceIdentityResponse> response =
                    ipcClient.verifyClientDeviceIdentity(request, Optional.empty()).getResponse();
            assertFalse(response.get(10, TimeUnit.SECONDS).isIsValidClientDevice());
        }
    }

    @Test
    void GIVEN_validRequestWithCloud500s_WHEN_verifyClientIdentity_THEN_returnsNotValid(ExtensionContext context)
            throws Exception {
        startNucleusWithConfig("cda.yaml");
        when(client.verifyClientDeviceIdentity(
                any(software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityRequest.class)))
                .thenThrow(InternalServerException.class);
        ignoreExceptionOfType(context, CloudServiceInteractionException.class);
        ignoreExceptionOfType(context, InternalServerException.class);
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest().withCredential(
                    new ClientDeviceCredential().withClientDeviceCertificate(validClientCertificatePem));

            CompletableFuture<VerifyClientDeviceIdentityResponse> response =
                    ipcClient.verifyClientDeviceIdentity(request, Optional.empty()).getResponse();
            assertFalse(response.get(10, TimeUnit.SECONDS).isIsValidClientDevice());
        }
    }
}
