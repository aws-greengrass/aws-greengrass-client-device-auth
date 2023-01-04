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
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.model.Metric;
import software.amazon.awssdk.aws.greengrass.model.MetricUnitType;

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
    private DomainEvents domainEvents;

    @BeforeEach
    void beforeEach() {
        domainEvents = new DomainEvents();
        metrics = new ClientDeviceAuthMetrics();
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
                .filter(m -> m.getValue().equals(0.0))
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
        Metric metric = new Metric();
        metric.setName(ClientDeviceAuthMetrics.METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS);
        metric.setValue(3.0);
        metric.setUnit(MetricUnitType.COUNT);

        Metric subscribeSuccess = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), subscribeSuccess.getValue());
        assertEquals(metric.getName(), subscribeSuccess.getName());
        assertEquals(metric.getUnit(), subscribeSuccess.getUnit());
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
        Metric metric = new Metric();
        metric.setName(ClientDeviceAuthMetrics.METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_FAILURE);
        metric.setValue(3.0);
        metric.setUnit(MetricUnitType.COUNT);

        Metric subscribeFail = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_FAILURE))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), subscribeFail.getValue());
        assertEquals(metric.getName(), subscribeFail.getName());
        assertEquals(metric.getUnit(), subscribeFail.getUnit());
    }

    @Test
    void GIVEN_verifyClientDeviceIdentityEvents_WHEN_eventsEmitted_THEN_verifyDeviceIdentitySuccessMetricCorrectlyEmitted() {
        // Emitting multiple Verify Client Device Identity events to ensure the metric is incremented correctly
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.SUCCESS));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.SUCCESS));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.SUCCESS));

        // Checking that the emitter collects the metrics as expected
        Metric metric = new Metric();
        metric.setName(ClientDeviceAuthMetrics.METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS);
        metric.setValue(3.0);
        metric.setUnit(MetricUnitType.COUNT);

        Metric verifyDeviceIdentitySuccess = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), verifyDeviceIdentitySuccess.getValue());
        assertEquals(metric.getName(), verifyDeviceIdentitySuccess.getName());
        assertEquals(metric.getUnit(), verifyDeviceIdentitySuccess.getUnit());
    }

    @Test
    void GIVEN_verifyClientDeviceIdentityEvents_WHEN_eventsEmitted_THEN_verifyDeviceIdentityFailMetricCorrectlyEmitted() {
        // Emitting multiple Verify Client Device Identity events to ensure the metric is incremented correctly
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.FAIL));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.FAIL));
        domainEvents.emit(new VerifyClientDeviceIdentityEvent(VerifyClientDeviceIdentityEvent.VerificationStatus.FAIL));

        // Checking that the emitter collects the metrics as expected
        Metric metric = new Metric();
        metric.setName(ClientDeviceAuthMetrics.METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_FAILURE);
        metric.setValue(3.0);
        metric.setUnit(MetricUnitType.COUNT);

        Metric verifyDeviceIdentityFail = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_FAILURE))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), verifyDeviceIdentityFail.getValue());
        assertEquals(metric.getName(), verifyDeviceIdentityFail.getName());
        assertEquals(metric.getUnit(), verifyDeviceIdentityFail.getUnit());
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
        Metric metric = new Metric();
        metric.setName(ClientDeviceAuthMetrics.METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS);
        metric.setValue(3.0);
        metric.setUnit(MetricUnitType.COUNT);

        Metric authorizeClientDeviceActionSuccess = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), authorizeClientDeviceActionSuccess.getValue());
        assertEquals(metric.getName(), authorizeClientDeviceActionSuccess.getName());
        assertEquals(metric.getUnit(), authorizeClientDeviceActionSuccess.getUnit());
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
        Metric metric = new Metric();
        metric.setName(ClientDeviceAuthMetrics.METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_FAILURE);
        metric.setValue(3.0);
        metric.setUnit(MetricUnitType.COUNT);

        Metric authorizeClientDeviceActionFailure = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_FAILURE))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), authorizeClientDeviceActionFailure.getValue());
        assertEquals(metric.getName(), authorizeClientDeviceActionFailure.getName());
        assertEquals(metric.getUnit(), authorizeClientDeviceActionFailure.getUnit());
    }

    @Test
    void GIVEN_getClientDeviceAuthTokenEvent_WHEN_eventsEmitted_THEN_getAuthTokenSuccessMetricCorrectlyEmitted() {
        // Emitting multiple Get Client Device Auth Token events to ensure the metric is incremented correctly
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.SUCCESS));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.SUCCESS));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.SUCCESS));

        // Checking that the emitter collects the metrics as expected
        Metric metric = new Metric();
        metric.setName(ClientDeviceAuthMetrics.METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS);
        metric.setValue(3.0);
        metric.setUnit(MetricUnitType.COUNT);

        Metric getAuthTokenSuccess = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), getAuthTokenSuccess.getValue());
        assertEquals(metric.getName(), getAuthTokenSuccess.getName());
        assertEquals(metric.getUnit(), getAuthTokenSuccess.getUnit());
    }

    @Test
    void GIVEN_getClientDeviceAuthTokenEvent_WHEN_eventsEmitted_THEN_getAuthTokenFailureMetricCorrectlyEmitted() {
        // Emitting multiple Get Client Device Auth Token events to ensure the metric is incremented correctly
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.FAILURE));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.FAILURE));
        domainEvents.emit(new SessionCreationEvent(SessionCreationEvent.SessionCreationStatus.FAILURE));

        // Checking that the emitter collects the metrics as expected
        Metric metric = new Metric();
        metric.setName(ClientDeviceAuthMetrics.METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_FAILURE);
        metric.setValue(3.0);
        metric.setUnit(MetricUnitType.COUNT);

        Metric getAuthTokenFailure = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_FAILURE))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), getAuthTokenFailure.getValue());
        assertEquals(metric.getName(), getAuthTokenFailure.getName());
        assertEquals(metric.getUnit(), getAuthTokenFailure.getUnit());
    }

    @Test
    void GIVEN_serviceErrorEvent_WHEN_eventsEmitted_THEN_serviceErrorMetricCorrectlyEmitted() {
        // Emitting multiple service error events to ensure the metric is incremented correctly
        domainEvents.emit(new ServiceErrorEvent());
        domainEvents.emit(new ServiceErrorEvent());
        domainEvents.emit(new ServiceErrorEvent());

        // Checking that the emitter collects the metrics as expected
        Metric metric = new Metric();
        metric.setName(ClientDeviceAuthMetrics.METRIC_SERVICE_ERROR);
        metric.setValue(3.0);
        metric.setUnit(MetricUnitType.COUNT);

        Metric serviceError = metrics.collectMetrics().stream()
                .filter(m -> m.getName().equals(ClientDeviceAuthMetrics.METRIC_SERVICE_ERROR))
                .findFirst()
                .orElseGet(() -> fail("metric not collected"));

        assertEquals(metric.getValue(), serviceError.getValue());
        assertEquals(metric.getName(), serviceError.getName());
        assertEquals(metric.getUnit(), serviceError.getUnit());
    }
}
