/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.ipc;

import com.aws.greengrass.dependency.State;
import com.aws.greengrass.device.ClientDevicesAuthService;
import com.aws.greengrass.device.DeviceAuthClient;
import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.lifecyclemanager.GlobalStateChangeListener;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.impl.config.LogConfig;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.UniqueRootPathExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.VerifyClientDeviceIdentityResponseHandler;
import software.amazon.awssdk.aws.greengrass.model.ClientDeviceCredential;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.aws.greengrass.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.aws.greengrass.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static com.aws.greengrass.testcommons.testutilities.TestUtils.asyncAssertOnConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock
    private DeviceAuthClient deviceAuthClient;

    private static void verifyClientIdentity(GreengrassCoreIPCClient ipcClient,
                                             VerifyClientDeviceIdentityRequest request,
                                             Consumer<VerifyClientDeviceIdentityResponse> consumer) throws Exception {
        VerifyClientDeviceIdentityResponseHandler handler =
                ipcClient.verifyClientDeviceIdentity(request, Optional.empty());
        VerifyClientDeviceIdentityResponse response = handler.getResponse().get(10, TimeUnit.SECONDS);
        consumer.accept(response);
    }

    @BeforeEach
    void beforeEach(ExtensionContext context) {
        ignoreExceptionOfType(context, SpoolerStoreException.class);

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);
        kernel.getContext().put(DeviceAuthClient.class, deviceAuthClient);
        lenient().when(deviceAuthClient.isGreengrassComponent(anyString())).thenReturn(false);

        when(clientFactory.getGreengrassV2DataClient()).thenReturn(client);

    }

    private void startNucleusWithConfig(String configFileName) throws InterruptedException {
        startNucleusWithConfig(configFileName, State.RUNNING);
    }

    private void startNucleusWithConfig(String configFileName, State expectedServiceState) throws InterruptedException {
        CountDownLatch authServiceRunning = new CountDownLatch(1);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        listener = (GreengrassService service, State was, State newState) -> {
            if (service.getName().equals(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME) &&
                    service.getState().equals(expectedServiceState)) {
                authServiceRunning.countDown();
            }
        };
        kernel.getContext().addGlobalStateChangeListener(listener);
        kernel.launch();
        assertThat(authServiceRunning.await(30L, TimeUnit.SECONDS), is(true));
        kernel.getContext().removeGlobalStateChangeListener(listener);
    }

    @AfterEach
    void afterEach() {
        LogConfig.getRootLogConfig().reset();
        kernel.shutdown();
    }

    @Test
    void GIVEN_broker_WHEN_verify_client_identity_THEN_verify() throws Exception {
        kernel.getContext().put(IotAuthClient.class, iotAuthClient);
        when(iotAuthClient.getActiveCertificateId(anyString())).thenReturn(
                Optional.of("SOME-CERT")
        );
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);

            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest()
                    .withCredential(new ClientDeviceCredential()
                            .withClientDeviceCertificate("abc"));

            Pair<CompletableFuture<Void>, Consumer<VerifyClientDeviceIdentityResponse>> cb =
                    asyncAssertOnConsumer((m) -> {
                        assertTrue(m.isIsValidClientDevice());
                    });

            verifyClientIdentity(ipcClient, request, cb.getRight());
            cb.getLeft().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void GIVEN_broker_WHEN_verify_client_identity_with_invalid_pem_THEN_verify() throws Exception {
        kernel.getContext().put(IotAuthClient.class, iotAuthClient);
        when(iotAuthClient.getActiveCertificateId(anyString())).thenReturn(
                Optional.empty()
        );
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);

            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest()
                    .withCredential(new ClientDeviceCredential()
                            .withClientDeviceCertificate("abc"));

            Pair<CompletableFuture<Void>, Consumer<VerifyClientDeviceIdentityResponse>> cb =
                    asyncAssertOnConsumer((m) -> {
                        assertFalse(m.isIsValidClientDevice());

                    });

            verifyClientIdentity(ipcClient, request, cb.getRight());
            cb.getLeft().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void GIVEN_broker_with_no_config_WHEN_verify_client_identity_THEN_error_is_thrown()
            throws ExecutionException, InterruptedException {
        kernel.getContext().put(IotAuthClient.class, iotAuthClient);
        startNucleusWithConfig("BrokerNotAuthorized.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithNoConfig")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest()
                    .withCredential(new ClientDeviceCredential()
                            .withClientDeviceCertificate("abc"));
            Exception err = Assertions.assertThrows(Exception.class, () -> {
                verifyClientIdentity(ipcClient, request, null);
            });
            assertThat(err.getCause(), is(instanceOf(UnauthorizedError.class)));
        }
    }


    @Test
    void GIVEN_broker_WHEN_verify_client_identity_with_validation_exception_THEN_error_is_thrown(
            ExtensionContext context)
            throws Exception {
        startNucleusWithConfig("cda.yaml");
        when(clientFactory.getGreengrassV2DataClient().verifyClientDeviceIdentity(
                any(software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityRequest.class)))
                .thenThrow(ValidationException.class);
        ignoreExceptionOfType(context, ValidationException.class);
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest()
                    .withCredential(new ClientDeviceCredential()
                            .withClientDeviceCertificate("abc"));

            Pair<CompletableFuture<Void>, Consumer<VerifyClientDeviceIdentityResponse>> cb =
                    asyncAssertOnConsumer((m) -> {
                        assertFalse(m.isIsValidClientDevice());

                    });

            verifyClientIdentity(ipcClient, request, cb.getRight());
            cb.getLeft().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void GIVEN_broker_WHEN_verify_client_identity_with_internal_server_exception_THEN_error_is_thrown(
            ExtensionContext context)
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
            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest()
                    .withCredential(new ClientDeviceCredential()
                            .withClientDeviceCertificate("abc"));

            Exception err = Assertions.assertThrows(Exception.class, () -> {
                verifyClientIdentity(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), ServiceError.class);
        }
    }

    @Test
    void GIVEN_broker_WHEN_verify_client_identity_with_exception_THEN_error_is_thrown(ExtensionContext context)
            throws ExecutionException, InterruptedException {
        startNucleusWithConfig("cda.yaml");
        ignoreExceptionOfType(context, NullPointerException.class);
        ignoreExceptionOfType(context, CloudServiceInteractionException.class);
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            VerifyClientDeviceIdentityRequest request = new VerifyClientDeviceIdentityRequest()
                    .withCredential(new ClientDeviceCredential()
                            .withClientDeviceCertificate("abc"));

            Exception err = Assertions.assertThrows(Exception.class, () -> {
                verifyClientIdentity(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), ServiceError.class);
        }
    }
}
