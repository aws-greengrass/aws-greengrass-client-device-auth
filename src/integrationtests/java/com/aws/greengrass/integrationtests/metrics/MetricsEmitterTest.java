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
import com.aws.greengrass.telemetry.AggregatedMetric;
import com.aws.greengrass.telemetry.AggregatedNamespaceData;
import com.aws.greengrass.telemetry.MetricsPayload;
import com.aws.greengrass.telemetry.impl.config.TelemetryConfig;
import com.aws.greengrass.telemetry.models.TelemetryUnit;
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
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.crt.mqtt.QualityOfService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class MetricsEmitterTest {
    private static final String MOCK_THING_NAME = "mockThing";
    private static final long TEST_TIME_OUT_SEC = 10L;
    private static final long AGGREGATE_INTERVAL = 2L;
    private static final int DEFAULT_PUBLISH_INTERVAL = 86_400;
    private Clock clock;
    private Kernel kernel;
    private DomainEvents domainEvents;
    @Mock
    private MqttClient mqttClient;
    @TempDir
    Path rootDir;

    @BeforeEach
    void beforeEach() {
        //Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        clock = Clock.systemUTC();
        domainEvents = new DomainEvents();
        kernel = new Kernel();
        kernel.getContext().put(DomainEvents.class, domainEvents);
        kernel.getContext().put(Clock.class, clock);
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

    private AggregatedMetric buildMetric(String name) {
        Map<String, Object> value = new HashMap<>();
        value.put("Sum", 1);
        AggregatedMetric m = AggregatedMetric.builder()
                .name(name)
                .unit(TelemetryUnit.Count)
                .value(value)
                .build();
        return m;
    }

    @Test
    void GIVEN_kernelRunningWithMetricsConfig_WHEN_launched_THEN_metricsArePublished(ExtensionContext context)
            throws DeviceConfigurationException, InterruptedException, IOException {
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

        //Build expected metrics
        AggregatedMetric expectedCertSubscribeSuccess = buildMetric(METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS);
        AggregatedMetric expectedAuthorizeClientDeviceActionSuccess =
                buildMetric(METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS);
        AggregatedMetric expectedVerifyClientDeviceIdentitySuccess =
                buildMetric(METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS);
        AggregatedMetric expectedGetClientDeviceAuthTokenSuccess =
                buildMetric(METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS);
        AggregatedMetric expectedServiceError = buildMetric(METRIC_SERVICE_ERROR);

        //WHEN
        startNucleusWithConfig("metricsConfig.yaml");
        int periodicAggregateIntervalSec = kernel.getContext().get(MetricsConfiguration.class)
                .getAggregationPeriodSeconds();
        assertEquals(AGGREGATE_INTERVAL, periodicAggregateIntervalSec);
        assertNotNull(kernel.getContext().get(MetricsEmitter.class).getFuture(),
                "periodic publish future is not scheduled");
        long delay = kernel.getContext().get(MetricsEmitter.class).getFuture().getDelay(TimeUnit.SECONDS);
        assertEquals(delay, periodicAggregateIntervalSec);

        //move clock one day ahead to trigger publish
        clock = Clock.offset(clock, Duration.ofSeconds(DEFAULT_PUBLISH_INTERVAL));

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

        List<AggregatedMetric> metrics = new ArrayList<>();
        for (PublishRequest request : requests) {
            if (!telemetryPublishTopic.equals(request.getTopic())) {
                continue;
            }
            MetricsPayload mp = new ObjectMapper().readValue(request.getPayload(), MetricsPayload.class);
            assertEquals(QualityOfService.AT_LEAST_ONCE, request.getQos());
            assertEquals("2022-06-30", mp.getSchema());
            telemetryMessageVerified = true;

            //The first aggregated namespace data should contain the metrics emitted
            List<AggregatedNamespaceData> aggregatedNamespaceData = mp.getAggregatedNamespaceData();
            metrics = aggregatedNamespaceData.get(0).getMetrics();
        }
        assertTrue(telemetryMessageVerified, "Did not see any message published to telemetry metrics topic");

        //Validate that the metrics were published correctly
        AggregatedMetric certSubscribeSuccess = metrics.stream()
                .filter(m -> m.getName().equals(METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("Subscribe to certificate updates success metric not published"));
        assertEquals(expectedCertSubscribeSuccess.getName(), certSubscribeSuccess.getName());
        assertEquals(expectedCertSubscribeSuccess.getUnit(), certSubscribeSuccess.getUnit());
        assertEquals(expectedCertSubscribeSuccess.getValue().get("Sum"), certSubscribeSuccess.getValue().get("Sum"));

        AggregatedMetric verifyClientDeviceIdentitySuccess = metrics.stream()
                .filter(m -> m.getName().equals(METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("Verify client device identity success metric not published"));
        assertEquals(expectedVerifyClientDeviceIdentitySuccess.getName(), verifyClientDeviceIdentitySuccess.getName());
        assertEquals(expectedVerifyClientDeviceIdentitySuccess.getUnit(), verifyClientDeviceIdentitySuccess.getUnit());
        assertEquals(expectedVerifyClientDeviceIdentitySuccess.getValue().get("Sum"),
                verifyClientDeviceIdentitySuccess.getValue().get("Sum"));

        AggregatedMetric authorizeClientDeviceActionSuccess = metrics.stream()
                .filter(m -> m.getName().equals(METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("Authorize client device action success metric not published"));
        assertEquals(expectedAuthorizeClientDeviceActionSuccess.getName(),
                authorizeClientDeviceActionSuccess.getName());
        assertEquals(expectedAuthorizeClientDeviceActionSuccess.getUnit(),
                authorizeClientDeviceActionSuccess.getUnit());
        assertEquals(expectedAuthorizeClientDeviceActionSuccess.getValue().get("Sum"),
                authorizeClientDeviceActionSuccess.getValue().get("Sum"));

        AggregatedMetric getClientDeviceAuthToken = metrics.stream()
                .filter(m -> m.getName().equals(METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("Get client device auth token success metric not published"));
        assertEquals(expectedGetClientDeviceAuthTokenSuccess.getName(), getClientDeviceAuthToken.getName());
        assertEquals(expectedGetClientDeviceAuthTokenSuccess.getUnit(), getClientDeviceAuthToken.getUnit());
        assertEquals(expectedGetClientDeviceAuthTokenSuccess.getValue().get("Sum"),
                getClientDeviceAuthToken.getValue().get("Sum"));

        AggregatedMetric serviceError = metrics.stream()
                .filter(m -> m.getName().equals(METRIC_SERVICE_ERROR))
                .findFirst()
                .orElseGet(() -> fail("Service error metric not published"));
        assertEquals(expectedServiceError.getName(), serviceError.getName());
        assertEquals(expectedServiceError.getUnit(), serviceError.getUnit());
        assertEquals(expectedServiceError.getValue().get("Sum"), serviceError.getValue().get("Sum"));
    }

}
