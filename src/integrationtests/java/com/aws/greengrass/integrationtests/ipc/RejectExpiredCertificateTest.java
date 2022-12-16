/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.ipc;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.lifecyclemanager.GlobalStateChangeListener;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.impl.config.LogConfig;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.UniqueRootPathExtension;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.model.ClientDeviceCredential;
import software.amazon.awssdk.aws.greengrass.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.aws.greengrass.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
class RejectExpiredCertificateTest {
    private static GlobalStateChangeListener listener;
    @TempDir
    Path rootDir;
    private Kernel kernel;
    private static X509Certificate clientCertificate;
    private static String clientCertificatePem;

    @BeforeAll
    static void beforeAll()
            throws CertificateException, NoSuchAlgorithmException, OperatorCreationException, IOException {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        KeyPair clientKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);

        clientCertificate =
                CertificateTestHelpers.createClientCertificate(rootCA, "Client", clientKeyPair.getPublic(),
                        rootKeyPair.getPrivate());
        clientCertificatePem = CertificateHelper.toPem(clientCertificate);
    }

    @BeforeEach
    void beforeEach(ExtensionContext context) throws InvalidCertificateException {
        ignoreExceptionOfType(context, SpoolerStoreException.class);
        ignoreExceptionOfType(context, NoSuchFileException.class); // Loading CA keystore

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();

        // Set up Iot auth client so that we ensure we're rejecting because the cert is expired
        IotAuthClientFake iotAuthClientFake = new IotAuthClientFake();
        iotAuthClientFake.activateCert(clientCertificatePem);
        kernel.getContext().put(IotAuthClient.class, iotAuthClientFake);
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
    void GIVEN_timeBeforeClientCertificateNotBefore_WHEN_verifyClientIdentity_THEN_returnsNotValid(
            ExtensionContext context) throws Exception {
        Instant beforeValid = clientCertificate.getNotBefore().toInstant().minusSeconds(1);
        kernel.getContext().put(Clock.class, Clock.fixed(beforeValid, ZoneId.systemDefault()));

        startNucleusWithConfig("cda.yaml");
        ignoreExceptionOfType(context, ValidationException.class);

        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest().withCredential(
                    new ClientDeviceCredential().withClientDeviceCertificate(clientCertificatePem));

            CompletableFuture<VerifyClientDeviceIdentityResponse> response =
                    ipcClient.verifyClientDeviceIdentity(request, Optional.empty()).getResponse();
            assertFalse(response.get(10, TimeUnit.SECONDS).isIsValidClientDevice());
        }
    }

    @Test
    void GIVEN_expiredClientCertificate_WHEN_verifyClientIdentity_THEN_returnsNotValid(ExtensionContext context)
            throws Exception {
        Instant beforeValid = clientCertificate.getNotAfter().toInstant().plusSeconds(1);
        kernel.getContext().put(Clock.class, Clock.fixed(beforeValid, ZoneId.systemDefault()));

        startNucleusWithConfig("cda.yaml");
        ignoreExceptionOfType(context, ValidationException.class);

        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest().withCredential(
                    new ClientDeviceCredential().withClientDeviceCertificate(clientCertificatePem));

            CompletableFuture<VerifyClientDeviceIdentityResponse> response =
                    ipcClient.verifyClientDeviceIdentity(request, Optional.empty()).getResponse();
            assertFalse(response.get(10, TimeUnit.SECONDS).isIsValidClientDevice());
        }
    }
}
