/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.ipc;

import com.aws.greengrass.dependency.State;
import com.aws.greengrass.device.ClientDevicesAuthService;
import com.aws.greengrass.device.DeviceAuthClient;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.lifecyclemanager.GlobalStateChangeListener;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.impl.config.LogConfig;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.UniqueRootPathExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.AuthorizeClientDeviceActionResponseHandler;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.model.AuthorizeClientDeviceActionRequest;
import software.amazon.awssdk.aws.greengrass.model.AuthorizeClientDeviceActionResponse;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.InvalidClientDeviceAuthTokenError;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static com.aws.greengrass.testcommons.testutilities.TestUtils.asyncAssertOnConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
class AuthorizeClientDeviceActionTest {
    private static GlobalStateChangeListener listener;
    @TempDir
    Path rootDir;
    private Kernel kernel;
    @Mock
    private GreengrassServiceClientFactory clientFactory;
    @Mock
    private GreengrassV2DataClient client;
    @Mock
    private DeviceAuthClient deviceAuthClient;

    private static void authzClientDeviceAction(GreengrassCoreIPCClient ipcClient,
                                               AuthorizeClientDeviceActionRequest request,
                                               Consumer<AuthorizeClientDeviceActionResponse> consumer)
            throws Exception {
        AuthorizeClientDeviceActionResponseHandler handler =
                ipcClient.authorizeClientDeviceAction(request, Optional.empty());
        AuthorizeClientDeviceActionResponse response = handler.getResponse().get(10, TimeUnit.SECONDS);
        consumer.accept(response);
    }

    @BeforeEach
    void beforeEach() {
        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);

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
    void GIVEN_brokerWithAuthorizedAction_WHEN_AuthorizeClientDeviceAction_THEN_returnTrue() throws Exception {
        kernel.getContext().put(DeviceAuthClient.class, deviceAuthClient);
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithAuthorizeClientDeviceActionPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            AuthorizeClientDeviceActionRequest request = new AuthorizeClientDeviceActionRequest()
                    .withOperation("some-action")
                    .withResource("some-resource")
                    .withClientDeviceAuthToken("some-token");
            when(deviceAuthClient.canDevicePerform(any())).thenReturn(true);
            Pair<CompletableFuture<Void>, Consumer<AuthorizeClientDeviceActionResponse>> cb =
                    asyncAssertOnConsumer((m) -> {
                        assertTrue(m.isIsAuthorized());
                    });
            authzClientDeviceAction(ipcClient, request, cb.getRight());
            cb.getLeft().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void GIVEN_brokerWithUnauthorizedAction_WHEN_AuthorizeClientDeviceAction_THEN_returnFalse() throws Exception {
        kernel.getContext().put(DeviceAuthClient.class, deviceAuthClient);
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithAuthorizeClientDeviceActionPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            AuthorizeClientDeviceActionRequest request = new AuthorizeClientDeviceActionRequest()
                    .withOperation("some-invalid-action")
                    .withResource("some-resource")
                    .withClientDeviceAuthToken("some-token");
            when(deviceAuthClient.canDevicePerform(any())).thenReturn(false);
            Pair<CompletableFuture<Void>, Consumer<AuthorizeClientDeviceActionResponse>> cb =
                    asyncAssertOnConsumer((m) -> {
                        assertFalse(m.isIsAuthorized());
                    });
            authzClientDeviceAction(ipcClient, request, cb.getRight());
            cb.getLeft().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void GIVEN_brokerWithInvalidAuthzRequest_WHEN_AuthorizeClientDeviceAction_THEN_throwInvalidArgumentsError()
            throws Exception {
        kernel.getContext().put(DeviceAuthClient.class, deviceAuthClient);
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithAuthorizeClientDeviceActionPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            AuthorizeClientDeviceActionRequest request = new AuthorizeClientDeviceActionRequest()
                    .withOperation("some-action")
                    .withResource(null)
                    .withClientDeviceAuthToken("some-token");
            Exception err = assertThrows(Exception.class, () -> {
                authzClientDeviceAction(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), InvalidArgumentsError.class);
        }
    }

    @Test
    void GIVEN_brokerWithInvalidAuthToken_WHEN_AuthorizeClientDeviceAction_THEN_throwInvalidClientDeviceAuthTokenError
            (ExtensionContext context) throws Exception {
        kernel.getContext().put(DeviceAuthClient.class, deviceAuthClient);
        ignoreExceptionOfType(context, AuthorizationException.class);
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithAuthorizeClientDeviceActionPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            AuthorizeClientDeviceActionRequest request = new AuthorizeClientDeviceActionRequest()
                    .withOperation("some-action")
                    .withResource("some-resource")
                    .withClientDeviceAuthToken("some-invalid-token");
            when(deviceAuthClient.canDevicePerform(any())).thenThrow(AuthorizationException.class);
            Exception err = assertThrows(Exception.class, () -> {
                authzClientDeviceAction(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), InvalidClientDeviceAuthTokenError.class);
        }
    }

    @Test
    void GIVEN_brokerWithValidAuthzRequest_WHEN_AuthorizeClientDeviceActionFails_THEN_throwServiceError
            (ExtensionContext context) throws Exception {
        kernel.getContext().put(DeviceAuthClient.class, deviceAuthClient);
        ignoreExceptionOfType(context, IllegalArgumentException.class);
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithAuthorizeClientDeviceActionPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            AuthorizeClientDeviceActionRequest request = new AuthorizeClientDeviceActionRequest()
                    .withOperation("some-action")
                    .withResource("some-resource")
                    .withClientDeviceAuthToken("some-token");
            when(deviceAuthClient.canDevicePerform(any())).thenThrow(IllegalArgumentException.class);
            Exception err = assertThrows(Exception.class, () -> {
                authzClientDeviceAction(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), ServiceError.class);
        }
    }

    @Test
    void GIVEN_brokerWithNoCDAPolicyConfig_WHEN_AuthorizeClientDeviceAction_THEN_throwUnauthorizedError()
            throws Exception {
        kernel.getContext().put(DeviceAuthClient.class, deviceAuthClient);
        startNucleusWithConfig("BrokerNotAuthorized.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithNoConfig")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            AuthorizeClientDeviceActionRequest request = new AuthorizeClientDeviceActionRequest()
                    .withOperation("some-action")
                    .withResource("some-resource")
                    .withClientDeviceAuthToken("some-token");
            Exception err = assertThrows(Exception.class, () -> {
                authzClientDeviceAction(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), UnauthorizedError.class);
        }
    }

    @Test
    void GIVEN_brokerWithMissingAuthzClientDeviceActionPermission_WHEN_AuthorizeClientDeviceAction_THEN_throwUnauthorizedError()
            throws Exception {
        kernel.getContext().put(DeviceAuthClient.class, deviceAuthClient);
        startNucleusWithConfig("BrokerNotAuthorized.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerMissingAuthorizeClientDeviceActionPolicy")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            AuthorizeClientDeviceActionRequest request = new AuthorizeClientDeviceActionRequest()
                    .withOperation("some-action")
                    .withResource("some-resource")
                    .withClientDeviceAuthToken("some-token");
            Exception err = assertThrows(Exception.class, () -> {
                authzClientDeviceAction(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), UnauthorizedError.class);
        }
    }
}
