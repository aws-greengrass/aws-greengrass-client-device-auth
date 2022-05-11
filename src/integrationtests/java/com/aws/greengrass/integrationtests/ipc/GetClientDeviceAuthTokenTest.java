/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.ipc;

import com.aws.greengrass.dependency.State;
import com.aws.greengrass.device.ClientDevicesAuthService;
import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.device.session.SessionManager;
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
import software.amazon.awssdk.aws.greengrass.GetClientDeviceAuthTokenResponseHandler;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.model.CredentialDocument;
import software.amazon.awssdk.aws.greengrass.model.GetClientDeviceAuthTokenRequest;
import software.amazon.awssdk.aws.greengrass.model.GetClientDeviceAuthTokenResponse;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.InvalidCredentialError;
import software.amazon.awssdk.aws.greengrass.model.MQTTCredential;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.utils.ImmutableMap;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;
import static com.aws.greengrass.device.ClientDevicesAuthService.CLOUD_QUEUE_SIZE_TOPIC;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static com.aws.greengrass.testcommons.testutilities.TestUtils.asyncAssertOnConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
class GetClientDeviceAuthTokenTest {
    private static GlobalStateChangeListener listener;
    @TempDir
    Path rootDir;
    private Kernel kernel;
    @Mock
    private GreengrassServiceClientFactory clientFactory;
    @Mock
    private GreengrassV2DataClient client;
    @Mock
    private SessionManager sessionManager;

    private static void clientDeviceAuthToken(GreengrassCoreIPCClient ipcClient,
                                              GetClientDeviceAuthTokenRequest request,
                                              Consumer<GetClientDeviceAuthTokenResponse> consumer) throws Exception {
        GetClientDeviceAuthTokenResponseHandler handler = ipcClient.getClientDeviceAuthToken(request, Optional.empty());
        GetClientDeviceAuthTokenResponse response = handler.getResponse().get(10, TimeUnit.SECONDS);
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
    void GIVEN_brokerWithValidCredentials_WHEN_GetClientDeviceAuthToken_THEN_returnsClientDeviceAuthToken()
            throws Exception {
        kernel.getContext().put(SessionManager.class, sessionManager);
        Map<String, String> mqttCredentialMap = ImmutableMap.of(
                "certificatePem", "VALID PEM",
                "clientId", "some-client-id",
                "username", null,
                "password", null);

        when(sessionManager
                .createSession("mqtt", mqttCredentialMap))
                .thenReturn("some-session-uuid");

        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithGetClientDeviceAuthTokenPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            MQTTCredential mqttCredential = new MQTTCredential().withClientId("some-client-id")
                    .withCertificatePem("VALID PEM");
            CredentialDocument cd = new CredentialDocument().withMqttCredential(mqttCredential);
            GetClientDeviceAuthTokenRequest request = new GetClientDeviceAuthTokenRequest().withCredential(cd);
            Pair<CompletableFuture<Void>, Consumer<GetClientDeviceAuthTokenResponse>> cb =
                    asyncAssertOnConsumer((m) -> {
                        assertEquals("some-session-uuid", m.getClientDeviceAuthToken());
                    });
            clientDeviceAuthToken(ipcClient, request, cb.getRight());
            cb.getLeft().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void GIVEN_brokerWithInvalidCredentials_WHEN_GetClientDeviceAuthToken_THEN_throwsInvalidArgumentsError_and_WHEN_queueIsFull_THEN_throwsServiceError()
            throws Exception {
        kernel.getContext().put(SessionManager.class, sessionManager);
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithGetClientDeviceAuthTokenPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            CredentialDocument cd = new CredentialDocument();
            GetClientDeviceAuthTokenRequest request = new GetClientDeviceAuthTokenRequest().withCredential(cd);

            Exception err = assertThrows(Exception.class, () -> {
                clientDeviceAuthToken(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), InvalidArgumentsError.class);
            assertThat(err.getCause().getMessage(), containsString("Invalid client device credentials"));
        }

        // Update the cloud queue size to 1 so that we'll just reject the second request
        kernel.findServiceTopic(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME)
                .lookup(CONFIGURATION_CONFIG_KEY, CLOUD_QUEUE_SIZE_TOPIC).withValue(1);
        kernel.getContext().waitForPublishQueueToClear();

        // Verify that we get a good error that the request couldn't be queued
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithGetClientDeviceAuthTokenPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            GetClientDeviceAuthTokenRequest request = new GetClientDeviceAuthTokenRequest().withCredential(
                    new CredentialDocument().withMqttCredential(
                            new MQTTCredential().withClientId("some-client-id").withCertificatePem("VALID PEM")));

            when(sessionManager.createSession(anyString(), anyMap())).thenAnswer((a) -> {
                Thread.sleep(1_000); // slow down the first request so that the second will be rejected
                return "uuid";
            });
            // Request 1 (immediately runs)
            CompletableFuture<GetClientDeviceAuthTokenResponse> fut1 =
                    ipcClient.getClientDeviceAuthToken(request, Optional.empty()).getResponse();
            // Request 2 (queued so that queue size is 1)
            CompletableFuture<GetClientDeviceAuthTokenResponse> fut2 =
                    ipcClient.getClientDeviceAuthToken(request, Optional.empty()).getResponse();
            // Request 3 (expect rejection)
            Exception err = assertThrows(Exception.class, () -> clientDeviceAuthToken(ipcClient, request, (r) -> {}));
            assertThat(err.getCause().getMessage(), containsString("Unable to queue request"));
            assertEquals(ServiceError.class, err.getCause().getClass());
            fut1.get(2, TimeUnit.SECONDS);
            fut2.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void GIVEN_brokerWithNoCDAPolicyConfig_WHEN_GetClientDeviceAuthToken_THEN_throwUnauthorizedError()
            throws Exception {
        kernel.getContext().put(SessionManager.class, sessionManager);
        startNucleusWithConfig("BrokerNotAuthorized.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithNoConfig")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            GetClientDeviceAuthTokenRequest request = new GetClientDeviceAuthTokenRequest()
                    .withCredential(null);

            Exception err = assertThrows(Exception.class, () -> {
                clientDeviceAuthToken(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), UnauthorizedError.class);

        }
    }

    @Test
    void GIVEN_brokerWithInvalidSession_WHEN_GetClientDeviceAuthToken_THEN_throwInvalidCredentialError(
            ExtensionContext context)
            throws Exception {
        kernel.getContext().put(SessionManager.class, sessionManager);
        ignoreExceptionOfType(context, AuthenticationException.class);
        startNucleusWithConfig("cda.yaml");
        Map<String, String> mqttCredentialMap = ImmutableMap.of(
                "certificatePem", "VALID PEM",
                "clientId", "some-client-id",
                "username", null,
                "password", null);
        when(sessionManager
                .createSession("mqtt", mqttCredentialMap))
                .thenThrow(AuthenticationException.class);

        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithGetClientDeviceAuthTokenPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            MQTTCredential mqttCredential = new MQTTCredential().withClientId("some-client-id")
                    .withCertificatePem("VALID PEM");
            CredentialDocument cd = new CredentialDocument().withMqttCredential(mqttCredential);
            GetClientDeviceAuthTokenRequest request = new GetClientDeviceAuthTokenRequest().withCredential(cd);

            Exception err = assertThrows(Exception.class, () -> {
                clientDeviceAuthToken(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), InvalidCredentialError.class);
        }
    }

    @Test
    void GIVEN_brokerWithValidCredentials_WHEN_GetClientDeviceAuthTokenFails_THEN_throwServiceError(
            ExtensionContext context)
            throws Exception {
        kernel.getContext().put(SessionManager.class, sessionManager);
        ignoreExceptionOfType(context, CloudServiceInteractionException.class);
        startNucleusWithConfig("cda.yaml");
        Map<String, String> mqttCredentialMap = ImmutableMap.of(
                "certificatePem", "VALID PEM",
                "clientId", "some-client-id",
                "username", null,
                "password", null);
        when(sessionManager
                .createSession("mqtt", mqttCredentialMap))
                .thenThrow(CloudServiceInteractionException.class);

        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithGetClientDeviceAuthTokenPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            MQTTCredential mqttCredential = new MQTTCredential().withClientId("some-client-id")
                    .withCertificatePem("VALID PEM");
            CredentialDocument cd = new CredentialDocument().withMqttCredential(mqttCredential);
            GetClientDeviceAuthTokenRequest request = new GetClientDeviceAuthTokenRequest().withCredential(cd);

            Exception err = assertThrows(Exception.class, () -> {
                clientDeviceAuthToken(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), ServiceError.class);
        }
    }

    @Test
    void GIVEN_brokerWithMissingGetClientDeviceAuthToken_WHEN_GetClientDeviceAuthToken_THEN_throwUnauthorizedError()
            throws Exception {
        kernel.getContext().put(SessionManager.class, sessionManager);
        startNucleusWithConfig("BrokerNotAuthorized.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerMissingGetClientDeviceAuthTokenPolicy")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            GetClientDeviceAuthTokenRequest request = new GetClientDeviceAuthTokenRequest()
                    .withCredential(null);

            Exception err = assertThrows(Exception.class, () -> {
                clientDeviceAuthToken(ipcClient, request, null);
            });
            assertEquals(err.getCause().getClass(), UnauthorizedError.class);

        }
    }


}
