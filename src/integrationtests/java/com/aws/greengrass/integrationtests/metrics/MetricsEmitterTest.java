/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.metrics;

import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.AuthorizeClientDeviceActionEvent;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.api.ServiceErrorEvent;
import com.aws.greengrass.clientdevices.auth.certificate.events.CertificateSubscriptionEvent;
import com.aws.greengrass.clientdevices.auth.iot.events.VerifyClientDeviceIdentityEvent;
import com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics;
import com.aws.greengrass.clientdevices.auth.metrics.MetricsEmitter;
import com.aws.greengrass.clientdevices.auth.session.events.SessionCreationEvent;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.models.TelemetryAggregation;
import com.aws.greengrass.telemetry.models.TelemetryUnit;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics.METRIC_SERVICE_ERROR;
import static com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics
        .METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS;
import static com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics
        .METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS;
import static com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics
        .METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS;
import static com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics
        .METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS;
import static com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics.NAMESPACE;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class MetricsEmitterTest {
    private static final long TEST_TIME_OUT_SEC = 10L;
    private Clock clock;
    private Kernel kernel;
    private DomainEvents domainEvents;
    private ClientDeviceAuthMetrics metricSpy;
    @Mock
    private GreengrassServiceClientFactory clientFactory;
    @Mock
    private GreengrassV2DataClient client;

    @TempDir
    Path rootDir;

    //Result captor class created to save and verify the results of scheduled emitting calls
    private class ResultCaptor<T> implements Answer {
        @Getter
        private T result = null;

        @Override
        public T answer(InvocationOnMock invocationOnMock) throws Throwable {
            result = (T) invocationOnMock.callRealMethod();
            return result;
        }
    }

    @BeforeEach
    void beforeEach(ExtensionContext context) throws DeviceConfigurationException {
        ignoreExceptionOfType(context, SdkClientException.class);
        ignoreExceptionOfType(context, SpoolerStoreException.class);
        ignoreExceptionOfType(context, NoSuchFileException.class); // Loading CA keystore

        //Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        clock = Clock.systemUTC();
        metricSpy = spy(new ClientDeviceAuthMetrics(clock));
        domainEvents = new DomainEvents();
        kernel = new Kernel();
        kernel.getContext().put(DomainEvents.class, domainEvents);
        kernel.getContext().put(Clock.class, clock);
        kernel.getContext().put(ClientDeviceAuthMetrics.class, metricSpy);
        kernel.getContext().put(GreengrassServiceClientFactory.class, clientFactory);
        lenient().when(clientFactory.fetchGreengrassV2DataClient()).thenReturn(client);
    }

    @AfterEach
    void afterEach() {
        if (kernel != null) {
            kernel.shutdown();
        }
    }

    private void startNucleusWithConfig(String configFileName) throws InterruptedException {
        CountDownLatch authServiceRunning = new CountDownLatch(1);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        kernel.getContext().addGlobalStateChangeListener((service, was, newState) -> {
            if (ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME.equals(service.getName())
                    && service.getState().equals(State.RUNNING)) {
                authServiceRunning.countDown();
            }
        });
        kernel.launch();
        assertThat(authServiceRunning.await(TEST_TIME_OUT_SEC, TimeUnit.SECONDS), is(true));
    }

    private Metric buildMetric(String name) {
        return Metric.builder()
                .namespace(NAMESPACE)
                .name(name)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(1L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();
    }

    @Test
    void GIVEN_kernelRunningWithMetricsConfig_WHEN_launched_THEN_metricsCorrectlyEmittedAtAggregationInterval()
            throws InterruptedException {
        startNucleusWithConfig("metricsConfig.yaml");
        assertNotNull(kernel.getContext().get(MetricsEmitter.class).getFuture(),
                "periodic publish future is not scheduled");

        //Emit metric events
        domainEvents.emit(new CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType.SERVER,
                CertificateSubscriptionEvent.SubscriptionStatus.SUCCESS));
        domainEvents.emit(new AuthorizeClientDeviceActionEvent(AuthorizeClientDeviceActionEvent
                .AuthorizationStatus.SUCCESS));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent
                .VerificationStatus.SUCCESS));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.SUCCESS));
        domainEvents.emit(new ServiceErrorEvent());

        //Create list of expected metrics to validate the metrics collected at the aggregation interval
        List<Metric> expectedMetrics = new ArrayList<>();
        expectedMetrics.add(buildMetric(METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS));
        expectedMetrics.add(buildMetric(METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS));
        expectedMetrics.add(buildMetric(METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS));
        expectedMetrics.add(buildMetric(METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS));
        expectedMetrics.add(buildMetric(METRIC_SERVICE_ERROR));

        //Capture the emitted metrics when the emitter runs
        ResultCaptor<List<Metric>> resultMetrics = new ResultCaptor<>();
        doAnswer(resultMetrics).when(metricSpy).collectMetrics();

        //Wait for emitter to run
        Thread.sleep(2000);

        //Verify that the correct metrics were emitted at startup
        verify(metricSpy, times(2)).emitMetrics();
        List<Metric> collectedMetrics = resultMetrics.getResult();
        assertEquals(expectedMetrics.size(), collectedMetrics.size());
        for (int i = 0; i < expectedMetrics.size(); i++) {
            assertEquals(expectedMetrics.get(i).getValue(), collectedMetrics.get(i).getValue());
            assertEquals(expectedMetrics.get(i).getNamespace(), collectedMetrics.get(i).getNamespace());
            assertEquals(expectedMetrics.get(i).getUnit(), collectedMetrics.get(i).getUnit());
            assertEquals(expectedMetrics.get(i).getAggregation(), collectedMetrics.get(i).getAggregation());
        }
    }
}
