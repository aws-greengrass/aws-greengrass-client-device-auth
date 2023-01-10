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
import com.aws.greengrass.clientdevices.auth.configuration.MetricsConfiguration;
import com.aws.greengrass.clientdevices.auth.iot.events.VerifyClientDeviceIdentityEvent;
import com.aws.greengrass.clientdevices.auth.metrics.MetricsEmitter;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.AuthorizeClientDeviceActionsMetricHandler;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.CertificateSubscriptionEventHandler;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.ServiceErrorEventHandler;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.SessionCreationEventHandler;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.VerifyClientDeviceIdentityEventHandler;
import com.aws.greengrass.clientdevices.auth.session.events.SessionCreationEvent;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.mqttclient.MqttClient;
import com.aws.greengrass.mqttclient.PublishRequest;
import com.aws.greengrass.telemetry.MetricsPayload;
import com.aws.greengrass.telemetry.impl.config.TelemetryConfig;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.exceptions.TLSAuthException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ScopedMock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.crt.mqtt.QualityOfService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.aws.greengrass.telemetry.TelemetryAgent.DEFAULT_TELEMETRY_METRICS_PUBLISH_TOPIC;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class MetricsEmitterTest {
    private static final String MOCK_THING_NAME = "mockThing";
    private static final long TEST_TIME_OUT_SEC = 10L;
    private static final long AGGREGATE_INTERVAL = 2L;
    private static final int DEFAULT_PUBLISH_INTERVAL = 86_400;
    private Optional<MockedStatic<Clock>> clockMock;
    private Kernel kernel;
    private DomainEvents domainEvents;
    @Mock
    private MqttClient mqttClient;
    @TempDir
    Path rootDir;

    @BeforeEach
    void beforeEach() {
        clockMock = Optional.empty();
        domainEvents = new DomainEvents();
        kernel = new Kernel();
        kernel.getContext().put(DomainEvents.class, domainEvents);
        kernel.getContext().get(CertificateSubscriptionEventHandler.class).listen();
        kernel.getContext().get(VerifyClientDeviceIdentityEventHandler.class).listen();
        kernel.getContext().get(AuthorizeClientDeviceActionsMetricHandler.class).listen();
        kernel.getContext().get(SessionCreationEventHandler.class).listen();
        kernel.getContext().get(ServiceErrorEventHandler.class).listen();
        TelemetryConfig.getInstance().telemetryLoggerNamesSet.clear();
        lenient().when(mqttClient.publish(any())).thenReturn(CompletableFuture.completedFuture(0));
    }

    @AfterEach
    void afterEach() {
        this.clockMock.ifPresent(ScopedMock::close);
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

    @SuppressWarnings("PMD.CloseResource")
    private void mockInstant(long expected) {
        this.clockMock.ifPresent(ScopedMock::close);
        Clock spyClock = spy(Clock.class);
        MockedStatic<Clock> clockMock;
        clockMock = mockStatic(Clock.class);
        clockMock.when(Clock::systemUTC).thenReturn(spyClock);
        when(spyClock.instant()).thenReturn(Instant.ofEpochMilli(expected));
        this.clockMock = Optional.of(clockMock);
    }


    @Test
    void GIVEN_kernelRunningWithMetricsConfig_WHEN_launched_THEN_metricsArePublished(ExtensionContext context)
            throws DeviceConfigurationException, InterruptedException {
        ignoreExceptionOfType(context, SdkClientException.class);
        ignoreExceptionOfType(context, TLSAuthException.class);
        //GIVEN
        kernel.getContext().put(MqttClient.class, mqttClient);
        kernel.getContext().put(DeviceConfiguration.class,
                new DeviceConfiguration(kernel, MOCK_THING_NAME, "us-east-1", "us-east-1", "mock", "mock", "mock",
                        "us-east-1", "mock"));

        //Emit metric events
        domainEvents.emit(new CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType.SERVER,
                CertificateSubscriptionEvent.SubscriptionStatus.SUCCESS));
        domainEvents.emit(new AuthorizeClientDeviceActionEvent(AuthorizeClientDeviceActionEvent
                .AuthorizationStatus.SUCCESS));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent
                .VerificationStatus.SUCCESS));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.SUCCESS));
        domainEvents.emit(new ServiceErrorEvent());

        //WHEN
        startNucleusWithConfig("metricsConfig.yaml");
        int periodicAggregateIntervalSec = kernel.getContext().get(MetricsConfiguration.class).getAggregatePeriod();
        assertEquals(AGGREGATE_INTERVAL, periodicAggregateIntervalSec);
        assertNotNull(kernel.getContext().get(MetricsEmitter.class).getFuture(),
                "periodic publish future is not scheduled");
        long delay = kernel.getContext().get(MetricsEmitter.class).getFuture().getDelay(TimeUnit.SECONDS);
        assertEquals(delay, periodicAggregateIntervalSec);

        //move clock one day ahead to trigger publish
        Instant aDayLater = Instant.now().plusSeconds(DEFAULT_PUBLISH_INTERVAL);
        mockInstant(aDayLater.toEpochMilli());

        //check for telemetry logs in ~root/telemetry
        assertEquals(kernel.getNucleusPaths().rootPath().resolve("telemetry"),
                TelemetryConfig.getTelemetryDirectory());

        //THEN
        boolean telemetryMessageVerified = false;
        String telemetryPublishTopic = DEFAULT_TELEMETRY_METRICS_PUBLISH_TOPIC
                .replace("{thingName}", MOCK_THING_NAME);
        CountDownLatch metricsPublished = new CountDownLatch(1);
        List<PublishRequest> requests = new ArrayList<>();
        when(mqttClient.publish(any(PublishRequest.class))).thenAnswer(i -> {
            Object argument = i.getArgument(0);
            PublishRequest publishRequest = (PublishRequest) argument;
            if (telemetryPublishTopic.equals(publishRequest.getTopic())) {
                requests.add(publishRequest);
                metricsPublished.countDown();
            }
            return CompletableFuture.completedFuture(0);
        });
        assertTrue(metricsPublished.await(30, TimeUnit.SECONDS), "Metrics not published");

        for (PublishRequest request : requests) {
            if (!telemetryPublishTopic.equals(request.getTopic())) {
                continue;
            }
            try {
                MetricsPayload mp = new ObjectMapper().readValue(request.getPayload(), MetricsPayload.class);
                assertEquals(QualityOfService.AT_LEAST_ONCE, request.getQos());
                assertEquals("2022-06-30", mp.getSchema());
                telemetryMessageVerified = true;
                break;
            } catch (IOException e) {
                fail("The message received at this topic is not of MetricsPayload type.", e);
            }
        }
        assertTrue(telemetryMessageVerified, "Did not see any message published to telemetry metrics topic");
    }

}
