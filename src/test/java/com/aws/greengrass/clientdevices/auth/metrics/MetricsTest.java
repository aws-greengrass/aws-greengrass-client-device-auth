/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.clientdevices.auth.api.AuthorizeClientDeviceActionEvent;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.api.ServiceErrorEvent;
import com.aws.greengrass.clientdevices.auth.certificate.events.CertificateSubscriptionEvent;
import com.aws.greengrass.clientdevices.auth.iot.events.VerifyClientDeviceIdentityEvent;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.AuthorizeClientDeviceActionsMetricHandler;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.CertificateSubscriptionEventHandler;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.ServiceErrorEventHandler;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.SessionCreationEventHandler;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.VerifyClientDeviceIdentityEventHandler;
import com.aws.greengrass.clientdevices.auth.session.events.SessionCreationEvent;
import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.models.TelemetryAggregation;
import com.aws.greengrass.telemetry.models.TelemetryUnit;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class MetricsTest {
    private ClientDeviceAuthMetrics metrics;
    private CertificateSubscriptionEventHandler certificateSubscriptionEventHandler;
    private VerifyClientDeviceIdentityEventHandler verifyClientDeviceIdentityEventHandler;
    private AuthorizeClientDeviceActionsMetricHandler authorizeClientDeviceActionsMetricHandler;
    private ServiceErrorEventHandler serviceErrorEventHandler;
    private SessionCreationEventHandler sessionCreationEventHandler;
    private Clock clock;
    private DomainEvents domainEvents;

    @BeforeEach
    void beforeEach() {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        domainEvents = new DomainEvents();
        metrics = new ClientDeviceAuthMetrics(clock);
        certificateSubscriptionEventHandler = new CertificateSubscriptionEventHandler(domainEvents, metrics);
        verifyClientDeviceIdentityEventHandler = new VerifyClientDeviceIdentityEventHandler(domainEvents, metrics);
        authorizeClientDeviceActionsMetricHandler = new AuthorizeClientDeviceActionsMetricHandler(domainEvents,
                metrics);
        sessionCreationEventHandler = new SessionCreationEventHandler(domainEvents, metrics);
        serviceErrorEventHandler = new ServiceErrorEventHandler(domainEvents, metrics);
        certificateSubscriptionEventHandler.listen();
        verifyClientDeviceIdentityEventHandler.listen();
        authorizeClientDeviceActionsMetricHandler.listen();
        sessionCreationEventHandler.listen();
        serviceErrorEventHandler.listen();
    }

    @Test
    void GIVEN_metricValues_WHEN_metricsCollected_THEN_onlyNonZeroValuesCollected() {
        // Incrementing the success metrics, leaving the failure metrics with values of 0
        domainEvents.emit(new CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType.SERVER,
                CertificateSubscriptionEvent.SubscriptionStatus.SUCCESS));
        domainEvents.emit(new AuthorizeClientDeviceActionEvent(AuthorizeClientDeviceActionEvent
                .AuthorizationStatus.SUCCESS));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent
                .VerificationStatus.SUCCESS));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.SUCCESS));

        List<Metric> collectedMetrics = metrics.collectMetrics();

        // Checking if any metrics with a value of 0 are included in the list
        long numZeroValueMetrics = collectedMetrics.stream()
                .filter(m -> m.getValue().equals(0L))
                .count();
        assertEquals(0, numZeroValueMetrics);

        // Checking that the size of the metrics list is as expected
        assertEquals(4, collectedMetrics.size());
    }

    @Test
    void GIVEN_certificateSubscriptionEvents_WHEN_eventsEmitted_THEN_subscribeSuccessMetricCorrectlyEmitted() {
        // Emitting multiple Certificate Subscription Events to ensure the metric is incremented correctly
        domainEvents.emit(new CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType.SERVER,
                CertificateSubscriptionEvent.SubscriptionStatus.SUCCESS));
        domainEvents.emit(new CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType.SERVER,
                CertificateSubscriptionEvent.SubscriptionStatus.SUCCESS));
        domainEvents.emit(new CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType.SERVER,
                CertificateSubscriptionEvent.SubscriptionStatus.SUCCESS));

        // Checking that the emitter collects the metrics as expected
        Metric metric = Metric.builder()
                .namespace("aws.greengrass.clientdevices.Auth")
                .name(ClientDeviceAuthMetrics.METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(3L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();

        Metric subscribeSuccess = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), subscribeSuccess.getValue());
        assertEquals(metric.getName(), subscribeSuccess.getName());
        assertEquals(metric.getTimestamp(), subscribeSuccess.getTimestamp());
        assertEquals(metric.getAggregation(), subscribeSuccess.getAggregation());
        assertEquals(metric.getUnit(), subscribeSuccess.getUnit());
        assertEquals(metric.getNamespace(), subscribeSuccess.getNamespace());
    }

    @Test
    void GIVEN_certificateSubscriptionEvents_WHEN_eventsEmitted_THEN_subscribeFailureMetricCorrectlyEmitted() {
        // Emitting multiple Certificate Subscription Events to ensure the metric is incremented correctly
        domainEvents.emit(new CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType.SERVER,
                CertificateSubscriptionEvent.SubscriptionStatus.FAIL));
        domainEvents.emit(new CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType.SERVER,
                CertificateSubscriptionEvent.SubscriptionStatus.FAIL));
        domainEvents.emit(new CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType.SERVER,
                CertificateSubscriptionEvent.SubscriptionStatus.FAIL));

        // Checking that the emitter collects the metrics as expected
        Metric metric = Metric.builder()
                .namespace("aws.greengrass.clientdevices.Auth")
                .name(ClientDeviceAuthMetrics.METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_FAILURE)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(3L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();

        Metric subscribeFail = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_FAILURE))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), subscribeFail.getValue());
        assertEquals(metric.getName(), subscribeFail.getName());
        assertEquals(metric.getTimestamp(), subscribeFail.getTimestamp());
        assertEquals(metric.getAggregation(), subscribeFail.getAggregation());
        assertEquals(metric.getUnit(), subscribeFail.getUnit());
        assertEquals(metric.getNamespace(), subscribeFail.getNamespace());
    }

    @Test
    void GIVEN_verifyClientDeviceIdentityEvents_WHEN_eventsEmitted_THEN_verifyDeviceIdentitySuccessMetricCorrectlyEmitted() {
        // Emitting multiple Verify Client Device Identity events to ensure the metric is incremented correctly
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.SUCCESS));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.SUCCESS));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.SUCCESS));

        // Checking that the emitter collects the metrics as expected
        Metric metric = Metric.builder()
                .namespace("aws.greengrass.clientdevices.Auth")
                .name(ClientDeviceAuthMetrics.METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(3L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();

        Metric verifyDeviceIdentitySuccess = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), verifyDeviceIdentitySuccess.getValue());
        assertEquals(metric.getName(), verifyDeviceIdentitySuccess.getName());
        assertEquals(metric.getTimestamp(), verifyDeviceIdentitySuccess.getTimestamp());
        assertEquals(metric.getAggregation(), verifyDeviceIdentitySuccess.getAggregation());
        assertEquals(metric.getUnit(), verifyDeviceIdentitySuccess.getUnit());
        assertEquals(metric.getNamespace(), verifyDeviceIdentitySuccess.getNamespace());
    }

    @Test
    void GIVEN_verifyClientDeviceIdentityEvents_WHEN_eventsEmitted_THEN_verifyDeviceIdentityFailMetricCorrectlyEmitted() {
        // Emitting multiple Verify Client Device Identity events to ensure the metric is incremented correctly
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.FAIL));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.FAIL));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.FAIL));

        // Checking that the emitter collects the metrics as expected
        Metric metric = Metric.builder()
                .namespace("aws.greengrass.clientdevices.Auth")
                .name(ClientDeviceAuthMetrics.METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_FAILURE)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(3L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();

        Metric verifyDeviceIdentityFail = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_FAILURE))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), verifyDeviceIdentityFail.getValue());
        assertEquals(metric.getName(), verifyDeviceIdentityFail.getName());
        assertEquals(metric.getTimestamp(), verifyDeviceIdentityFail.getTimestamp());
        assertEquals(metric.getAggregation(), verifyDeviceIdentityFail.getAggregation());
        assertEquals(metric.getUnit(), verifyDeviceIdentityFail.getUnit());
        assertEquals(metric.getNamespace(), verifyDeviceIdentityFail.getNamespace());
    }

    @Test
    void GIVEN_authorizeClientDeviceActionEvent_WHEN_eventsEmitted_THEN_authorizeDeviceActionSuccessCorrectlyEmitted() {
        // Emitting multiple Authorize Client Device Action events to ensure the metric is incremented correctly
        domainEvents.emit(new AuthorizeClientDeviceActionEvent(AuthorizeClientDeviceActionEvent
                .AuthorizationStatus.SUCCESS));
        domainEvents.emit(new AuthorizeClientDeviceActionEvent(AuthorizeClientDeviceActionEvent
                .AuthorizationStatus.SUCCESS));
        domainEvents.emit(new AuthorizeClientDeviceActionEvent(AuthorizeClientDeviceActionEvent
                .AuthorizationStatus.SUCCESS));

        // Checking that the emitter collects the metrics as expected
        Metric metric = Metric.builder()
                .namespace("aws.greengrass.clientdevices.Auth")
                .name(ClientDeviceAuthMetrics.METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(3L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();

        Metric authorizeClientDeviceActionSuccess = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), authorizeClientDeviceActionSuccess.getValue());
        assertEquals(metric.getName(), authorizeClientDeviceActionSuccess.getName());
        assertEquals(metric.getTimestamp(), authorizeClientDeviceActionSuccess.getTimestamp());
        assertEquals(metric.getAggregation(), authorizeClientDeviceActionSuccess.getAggregation());
        assertEquals(metric.getUnit(), authorizeClientDeviceActionSuccess.getUnit());
        assertEquals(metric.getNamespace(), authorizeClientDeviceActionSuccess.getNamespace());
    }

    @Test
    void GIVEN_authorizeClientDeviceActionEvent_WHEN_eventsEmitted_THEN_authorizeDeviceActionFailureCorrectlyEmitted() {
        // Emitting multiple Authorize Client Device Action events to ensure the metric is incremented correctly
        domainEvents.emit(new AuthorizeClientDeviceActionEvent(AuthorizeClientDeviceActionEvent
                .AuthorizationStatus.FAIL));
        domainEvents.emit(new AuthorizeClientDeviceActionEvent(AuthorizeClientDeviceActionEvent
                .AuthorizationStatus.FAIL));
        domainEvents.emit(new AuthorizeClientDeviceActionEvent(AuthorizeClientDeviceActionEvent
                .AuthorizationStatus.FAIL));

        // Checking that the emitter collects the metrics as expected
        Metric metric = Metric.builder()
                .namespace("aws.greengrass.clientdevices.Auth")
                .name(ClientDeviceAuthMetrics.METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_FAILURE)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(3L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();

        Metric authorizeClientDeviceActionFailure = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_FAILURE))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), authorizeClientDeviceActionFailure.getValue());
        assertEquals(metric.getName(), authorizeClientDeviceActionFailure.getName());
        assertEquals(metric.getTimestamp(), authorizeClientDeviceActionFailure.getTimestamp());
        assertEquals(metric.getAggregation(), authorizeClientDeviceActionFailure.getAggregation());
        assertEquals(metric.getUnit(), authorizeClientDeviceActionFailure.getUnit());
        assertEquals(metric.getNamespace(), authorizeClientDeviceActionFailure.getNamespace());
    }

    @Test
    void GIVEN_getClientDeviceAuthTokenEvent_WHEN_eventsEmitted_THEN_getAuthTokenSuccessMetricCorrectlyEmitted() {
        // Emitting multiple Get Client Device Auth Token events to ensure the metric is incremented correctly
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.SUCCESS));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.SUCCESS));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.SUCCESS));

        // Checking that the emitter collects the metrics as expected
        Metric metric = Metric.builder()
                .namespace("aws.greengrass.clientdevices.Auth")
                .name(ClientDeviceAuthMetrics.METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(3L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();

        Metric getAuthTokenSuccess = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), getAuthTokenSuccess.getValue());
        assertEquals(metric.getName(), getAuthTokenSuccess.getName());
        assertEquals(metric.getTimestamp(), getAuthTokenSuccess.getTimestamp());
        assertEquals(metric.getAggregation(), getAuthTokenSuccess.getAggregation());
        assertEquals(metric.getUnit(), getAuthTokenSuccess.getUnit());
        assertEquals(metric.getNamespace(), getAuthTokenSuccess.getNamespace());
    }

    @Test
    void GIVEN_getClientDeviceAuthTokenEvent_WHEN_eventsEmitted_THEN_getAuthTokenFailureMetricCorrectlyEmitted() {
        // Emitting multiple Get Client Device Auth Token events to ensure the metric is incremented correctly
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.FAILURE));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.FAILURE));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.FAILURE));

        // Checking that the emitter collects the metrics as expected
        Metric metric = Metric.builder()
                .namespace("aws.greengrass.clientdevices.Auth")
                .name(ClientDeviceAuthMetrics.METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_FAILURE)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(3L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();

        Metric getAuthTokenFailure = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_FAILURE))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), getAuthTokenFailure.getValue());
        assertEquals(metric.getName(), getAuthTokenFailure.getName());
        assertEquals(metric.getTimestamp(), getAuthTokenFailure.getTimestamp());
        assertEquals(metric.getAggregation(), getAuthTokenFailure.getAggregation());
        assertEquals(metric.getUnit(), getAuthTokenFailure.getUnit());
        assertEquals(metric.getNamespace(), getAuthTokenFailure.getNamespace());
    }

    @Test
    void GIVEN_serviceErrorEvent_WHEN_eventsEmitted_THEN_serviceErrorMetricCorrectlyEmitted() {
        // Emitting multiple service error events to ensure the metric is incremented correctly
        domainEvents.emit(new ServiceErrorEvent());
        domainEvents.emit(new ServiceErrorEvent());
        domainEvents.emit(new ServiceErrorEvent());

        // Checking that the emitter collects the metrics as expected
        Metric metric = Metric.builder()
                .namespace("aws.greengrass.clientdevices.Auth")
                .name(ClientDeviceAuthMetrics.METRIC_SERVICE_ERROR)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(3L)
                .timestamp(Instant.now(clock).toEpochMilli())
                .build();

        Metric serviceError = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_SERVICE_ERROR))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), serviceError.getValue());
        assertEquals(metric.getName(), serviceError.getName());
        assertEquals(metric.getTimestamp(), serviceError.getTimestamp());
        assertEquals(metric.getAggregation(), serviceError.getAggregation());
        assertEquals(metric.getUnit(), serviceError.getUnit());
        assertEquals(metric.getNamespace(), serviceError.getNamespace());
    }
}
