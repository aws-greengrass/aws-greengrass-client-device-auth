/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.ipc;

import com.aws.greengrass.dependency.State;
import com.aws.greengrass.device.ClientDevicesAuthService;
import com.aws.greengrass.lifecyclemanager.GlobalStateChangeListener;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.impl.config.LogConfig;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.UniqueRootPathExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.model.CertificateOptions;
import software.amazon.awssdk.aws.greengrass.model.CertificateType;
import software.amazon.awssdk.aws.greengrass.model.CertificateUpdate;
import software.amazon.awssdk.aws.greengrass.model.CertificateUpdateEvent;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToCertificateUpdatesRequest;
import software.amazon.awssdk.aws.greengrass.model.UnauthorizedError;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.eventstreamrpc.StreamResponseHandler;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.aws.greengrass.testcommons.testutilities.TestUtils.asyncAssertOnConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.lenient;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
class SubscribeToCertificateUpdatesTest {
    private static GlobalStateChangeListener listener;
    @TempDir
    Path rootDir;
    private Kernel kernel;
    @Mock
    private GreengrassServiceClientFactory clientFactory;
    @Mock
    private GreengrassV2DataClient client;

    private static void subscribeToCertUpdates(GreengrassCoreIPCClient ipcClient,
                                              SubscribeToCertificateUpdatesRequest request,
                                              Consumer<CertificateUpdate> consumer) throws Exception {
        ipcClient.subscribeToCertificateUpdates(request,
                Optional.of(new StreamResponseHandler<CertificateUpdateEvent>() {
                    @Override
                    public void onStreamEvent(CertificateUpdateEvent certificateUpdateEvent) {
                        consumer.accept(certificateUpdateEvent.getCertificateUpdate());
                    }

                    @Override
                    public boolean onStreamError(Throwable error) {
                        return false;
                    }

                    @Override
                    public void onStreamClosed() {
                    }
                })).getResponse().get(10, TimeUnit.SECONDS);
    }

    private static SubscribeToCertificateUpdatesRequest getSampleSubsRequest() {
        SubscribeToCertificateUpdatesRequest subscribeToCertificateUpdatesRequest =
                new SubscribeToCertificateUpdatesRequest();
        subscribeToCertificateUpdatesRequest.setCertificateOptions(new CertificateOptions().withCertificateType(
                CertificateType.SERVER));
        return subscribeToCertificateUpdatesRequest;
    }

    @BeforeEach
    void beforeEach() {
        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);
        lenient().when(clientFactory.getGreengrassV2DataClient()).thenReturn(client);
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
    void GIVEN_two_brokers_WHEN_subscribed_to_certificate_updates_THEN_both_receive_updates_on_stream()
            throws Exception {
        startNucleusWithConfig("cda.yaml");
        Map<String, String> broker1Certs = new HashMap<>();
        Map<String, String> broker2Certs = new HashMap<>();
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            SubscribeToCertificateUpdatesRequest request = getSampleSubsRequest();
            Pair<CompletableFuture<Void>, Consumer<CertificateUpdate>> cb = asyncAssertOnConsumer((m) -> {
                broker1Certs.put("certificate", m.getCertificate());
                broker1Certs.put("ca-certificate", m.getCaCertificates().get(0));
            });

            subscribeToCertUpdates(ipcClient, request, cb.getRight());
            cb.getLeft().get(10, TimeUnit.SECONDS);
        }

        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "Broker2SubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            SubscribeToCertificateUpdatesRequest request = getSampleSubsRequest();
            Pair<CompletableFuture<Void>, Consumer<CertificateUpdate>> cb = asyncAssertOnConsumer((m) -> {
                broker2Certs.put("certificate", m.getCertificate());
                broker2Certs.put("ca-certificate", m.getCaCertificates().get(0));
            });

            subscribeToCertUpdates(ipcClient, request, cb.getRight());
            cb.getLeft().get(10, TimeUnit.SECONDS);
            assertThat(broker1Certs.get("ca-certificate"), is(broker2Certs.get("ca-certificate")));
            assertThat(broker1Certs.get("certificate"), is(not(broker2Certs.get("certificate"))));
        }
    }

    @Test
    void GIVEN_broker_WHEN_subscribed_to_certificate_updates_THEN_certificate_is_generated() throws Exception {
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            SubscribeToCertificateUpdatesRequest request = getSampleSubsRequest();
            Pair<CompletableFuture<Void>, Consumer<CertificateUpdate>> cb = asyncAssertOnConsumer((m) -> {
                assertThat(m.getCertificate(), startsWith("-----BEGIN CERTIFICATE-----"));
                assertThat(m.getCaCertificates().get(0), startsWith("-----BEGIN CERTIFICATE-----"));
                assertThat(m.getPrivateKey(), startsWith("-----BEGIN PRIVATE KEY-----"));
                assertThat(m.getPublicKey(), startsWith("-----BEGIN PUBLIC KEY-----"));

                assertThat(m.getCertificate(), endsWith("-----END CERTIFICATE-----" + System.lineSeparator()));
                assertThat(m.getCaCertificates().get(0), endsWith("-----END CERTIFICATE-----" + System.lineSeparator()));
                assertThat(m.getPrivateKey(), endsWith("-----END PRIVATE KEY-----" + System.lineSeparator()));
                assertThat(m.getPublicKey(), endsWith("-----END PUBLIC KEY-----" + System.lineSeparator()));

            });

            subscribeToCertUpdates(ipcClient, request, cb.getRight());
            cb.getLeft().get(10, TimeUnit.SECONDS);
        }
    }


    @Test
    void GIVEN_broker_with_no_config_WHEN_subscribed_to_certificate_updates_THEN_error_is_thrown()
            throws ExecutionException, InterruptedException {
        startNucleusWithConfig("BrokerNotAuthorized.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithNoConfig")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
            SubscribeToCertificateUpdatesRequest request = getSampleSubsRequest();
            Pair<CompletableFuture<Void>, Consumer<CertificateUpdate>> cb = asyncAssertOnConsumer((m) -> {
            });
            Exception err = Assertions.assertThrows(Exception.class, () -> {
                subscribeToCertUpdates(ipcClient, request, cb.getRight());
            });
            assertThat(err.getCause(), is(instanceOf(UnauthorizedError.class)));
        }
    }

    @Test
    void GIVEN_broker_WHEN_subscribed_to_cert_update_with_invalid_cert_type_THEN_error_is_thrown()
            throws ExecutionException, InterruptedException {
        startNucleusWithConfig("cda.yaml");
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerSubscribingToCertUpdates")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);
                SubscribeToCertificateUpdatesRequest request = new SubscribeToCertificateUpdatesRequest();
                request.withCertificateOptions(new CertificateOptions().withCertificateType("INVALID-TYPE"));


            Pair<CompletableFuture<Void>, Consumer<CertificateUpdate>> cb = asyncAssertOnConsumer((m) -> {
            });
            Exception err = Assertions.assertThrows(Exception.class, () -> {
                subscribeToCertUpdates(ipcClient, request, cb.getRight());
            });
            assertThat(err.getCause(), is(instanceOf(InvalidArgumentsError.class)));
            assertThat(err.getMessage(),containsString("Valid certificate type is required."));
        }
    }

    // TODO: Integ test to check if rotated cert updates are received (update cert expiry values)
}
