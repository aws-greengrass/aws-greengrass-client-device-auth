/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.connectivity;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformationSource;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.clientdevices.auth.connectivity.RecordConnectivityChangesRequest;
import com.aws.greengrass.clientdevices.auth.connectivity.usecases.RecordConnectivityChangesUseCase;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.clientdevices.auth.iot.NetworkStateFake;
import com.aws.greengrass.dependency.Crashable;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.integrationtests.ipc.IPCTestUtils;
import com.aws.greengrass.lifecyclemanager.GlobalStateChangeListener;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.impl.config.LogConfig;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.UniqueRootPathExtension;
import com.aws.greengrass.util.CrashableFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClientV2;
import software.amazon.awssdk.aws.greengrass.model.CertificateOptions;
import software.amazon.awssdk.aws.greengrass.model.CertificateType;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToCertificateUpdatesRequest;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
public class ConnectivityTest {

    NetworkStateFake networkState;
    @TempDir
    Path rootDir;
    Kernel kernel;

    @BeforeEach
    void beforeEach(ExtensionContext context) {
        ignoreExceptionOfType(context, SpoolerStoreException.class);
        // expected when creating the keystore for the first time
        ignoreExceptionOfType(context, NoSuchFileException.class);
        networkState = new NetworkStateFake();
        setupNewKernel();
    }

    @AfterEach
    void afterEach() {
        LogConfig.getRootLogConfig().reset();
        kernel.shutdown();
    }

    @Test
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    void GIVEN_cda_starts_up_and_connectivity_information_is_acquired_WHEN_cda_restarts_offline_THEN_cached_connectivity_information_is_used() throws Exception {
        String confFile = "config.yaml";
        String serviceNameIpc = "BrokerSubscribingToCertUpdates";

        Set<HostAddress> updatedConnectivityInfo = new HashSet<>();
        updatedConnectivityInfo.add(HostAddress.of("123.123.123.123"));

        Set<String> expectedSANs = updatedConnectivityInfo.stream()
                .map(HostAddress::getHost)
                .collect(Collectors.toSet());
        expectedSANs.add("localhost");

        // start up nucleus
        startNucleusWithConfig(confFile);

        // update connectivity info
        // TODO replace with CIS shadow update to make this more of an integration test
        kernel.getContext().get(UseCases.class).get(RecordConnectivityChangesUseCase.class)
                .apply(new RecordConnectivityChangesRequest(ConnectivityInformationSource.CONNECTIVITY_INFORMATION_SERVICE, updatedConnectivityInfo));

        // wait until server certificate is generated.
        // ensure that it has the ip address we configure
        withServerCert(serviceNameIpc, cert ->
                () -> assertEquals(expectedSANs, dnsNamesFromSAN(cert.getSubjectAlternativeNames())));

        // shutdown the nucleus
        kernel.shutdown();

        // take greengrass offline
        networkState.goOffline();
        kernel.getContext().put(NetworkStateProvider.class, networkState);

        // start the nucleus offline
        restartNucleus();

        // verify that server cert is still generated with the proper SANs
        withServerCert(serviceNameIpc, cert ->
                () -> assertEquals(expectedSANs, dnsNamesFromSAN(cert.getSubjectAlternativeNames())));
    }

    private void setupNewKernel() {
        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();

        // Set up Iot auth client
        IotAuthClientFake iotAuthClientFake = new IotAuthClientFake();
        kernel.getContext().put(IotAuthClient.class, iotAuthClientFake);
    }

    private void startNucleusWithConfig(String configFileName) throws InterruptedException {
        startNucleusWithConfig(configFileName, ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME, State.RUNNING);
    }

    private void restartNucleus() throws InterruptedException {
        setupNewKernel();
        startNucleusWithConfig(null, ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME, State.RUNNING);
    }

    private void startNucleusWithConfig(String configFileName, String serviceName, State desiredServiceState)
            throws InterruptedException {
        CountDownLatch serviceReachedDesiredState = new CountDownLatch(1);

        String[] args = configFileName == null
                ? new String[]{"-r", rootDir.toAbsolutePath().toString()}
                : new String[]{"-r", rootDir.toAbsolutePath().toString(),
                    "-i", getClass().getResource(configFileName).toString()};

        kernel.parseArgs(args);

        GlobalStateChangeListener listener = (GreengrassService service, State was, State newState) -> {
            if (service.getName().equals(serviceName) && service.getState().equals(desiredServiceState)) {
                serviceReachedDesiredState.countDown();
            }
        };
        kernel.getContext().addGlobalStateChangeListener(listener);
        try {
            kernel.launch();
            assertThat(serviceReachedDesiredState.await(30L, TimeUnit.SECONDS), is(true));
        } finally {
            kernel.getContext().removeGlobalStateChangeListener(listener);
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    private void withServerCert(String serviceNameIpc, CrashableFunction<X509Certificate, Crashable, Exception> operation)
            throws Exception {
        withIPCClient(serviceNameIpc, client -> {
            CountDownLatch operationComplete = new CountDownLatch(1);

            SubscribeToCertificateUpdatesRequest subscribeRequest = new SubscribeToCertificateUpdatesRequest()
                    .withCertificateOptions(new CertificateOptions().withCertificateType(CertificateType.SERVER));
            client.subscribeToCertificateUpdates(
                    subscribeRequest,
                    certUpdate -> {
                        try {
                            List<X509Certificate>  cert = CertificateTestHelpers.loadX509Certificate(
                                    certUpdate.getCertificateUpdate().getCertificate());
                            assertFalse(cert.isEmpty());
                            operation.apply(cert.get(0)).run();
                            operationComplete.countDown();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    },
                    Optional.of((ignore) -> null),
                    Optional.of(() -> {})
            );

            assertTrue(operationComplete.await(10L, TimeUnit.SECONDS));
            return null;
        });
    }

    private void withIPCClient(String serviceName, CrashableFunction<GreengrassCoreIPCClientV2, Void, Exception> action)
            throws Exception {
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                serviceName);
             GreengrassCoreIPCClientV2 client = GreengrassCoreIPCClientV2.builder()
                     .withClient(new GreengrassCoreIPCClient(connection))
                     .build()) {
            action.apply(client);
        }
    }

    private static Set<String> dnsNamesFromSAN(Collection<List<?>> sans) {
        if (sans == null) {
            return Collections.emptySet();
        }
        return sans.stream()
                .filter(entry -> {
                    int dnsNameKey = 2;
                    int ipAddressKey = 7;
                    return entry.size() >= 2
                            && (Objects.equals(entry.get(0), dnsNameKey) || Objects.equals(entry.get(0), ipAddressKey));
                })
                .flatMap(entry -> entry.subList(1, entry.size()).stream())
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }
}
