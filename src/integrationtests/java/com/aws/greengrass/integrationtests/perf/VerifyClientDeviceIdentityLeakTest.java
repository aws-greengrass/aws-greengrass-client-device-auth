/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.perf;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.GreengrassV2DataClientFactory;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.lifecyclemanager.GlobalStateChangeListener;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.impl.config.LogConfig;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.UniqueRootPathExtension;
import com.aws.greengrass.util.ProxyUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
class VerifyClientDeviceIdentityLeakTest {
    private static GlobalStateChangeListener listener;
    @TempDir
    Path rootDir;
    private Kernel kernel;
    private HeapTracker heapTracker;

    @BeforeEach
    void beforeEach(ExtensionContext context) {
        ignoreExceptionOfType(context, SpoolerStoreException.class);

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();

        kernel.getContext().put(NetworkStateProvider.class, new NetworkStateProvider() {
            @Override
            public void registerHandler(Consumer<ConnectionState> networkChangeHandler) {
            }

            @Override
            public ConnectionState getConnectionState() {
                return ConnectionState.NETWORK_UP;
            }
        });
        kernel.getContext().put(GreengrassV2DataClientFactory.class,
                new CustomGreengrassV2DataClientFactory(kernel.getContext().get(DeviceConfiguration.class)));

        heapTracker = new HeapTracker();
        heapTracker.start();
    }

    @AfterEach
    void afterEach() {
        LogConfig.getRootLogConfig().reset();
        kernel.shutdown();
        heapTracker.stop();
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
        startNucleusWithConfig("cda.yaml");
        IotAuthClient authClient = kernel.getContext().get(IotAuthClient.class);
        while (true) {
            authClient.getActiveCertificateId("pem");
        }
    }

    static class HeapTracker {

        long currFreeMemory;
        long currTotalMemory;
        double maxPercHeapUsed;

        ScheduledExecutorService ses;
        ScheduledFuture<?> pollTask;

        HeapTracker() {
            ses = Executors.newScheduledThreadPool(1);
        }

        synchronized void start() {
            pollTask = ses.scheduleAtFixedRate(this::poll, 0, 1, TimeUnit.SECONDS);
        }

        private void poll() {
            this.currFreeMemory = Runtime.getRuntime().freeMemory();
            this.currTotalMemory = Runtime.getRuntime().totalMemory();

            double percHeapUsed = ((currTotalMemory - currFreeMemory) / (double) currTotalMemory) * 100.0;
            if (percHeapUsed > maxPercHeapUsed) {
                this.maxPercHeapUsed = percHeapUsed;
                System.out.println("Heap usage increased: " + maxPercHeapUsed + "%");
            }
        }

        synchronized void stop() {
            if (pollTask != null) {
                pollTask.cancel(true);
            }
            if (ses != null) {
                ses.shutdownNow();
            }
        }
    }

    class CustomGreengrassV2DataClientFactory extends GreengrassV2DataClientFactory {

        public CustomGreengrassV2DataClientFactory(DeviceConfiguration deviceConfiguration) {
            super(deviceConfiguration);
        }

        @Override
        protected ApacheHttpClient.Builder getHttpClientBuilder() {
            return ProxyUtils.getSdkHttpClientBuilder();
        }

        @Override
        public GreengrassV2DataClient getClient() throws DeviceConfigurationException {
            GreengrassV2DataClient mock = mock(GreengrassV2DataClient.class);
            GreengrassV2DataClient real =  super.getClient();

            // force http client to do something?
            try {
                real.verifyClientDeviceIdentity(b -> b.clientDeviceCertificate(""));
            } catch (Exception ignore){}

            software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityResponse resp =
                    mock(software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityResponse.class);
            when(resp.clientDeviceCertificateId()).thenReturn("");

            when(mock.verifyClientDeviceIdentity(any(software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityRequest.class)))
                    .thenReturn(resp);

            doAnswer(inv -> {
                real.close();
                return null;
            }).when(mock).close();

            return mock;
        }
    }
}
