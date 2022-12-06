/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.certificate.events.CertificateSubscriptionEvent;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.CertificateSubscriptionHandler;
import com.aws.greengrass.clientdevices.auth.metrics.handlers.VerifyClientDeviceIdentityHandler;
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

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class MetricsTest {
    private ClientDeviceAuthMetrics metrics;
    private CertificateSubscriptionHandler certificateSubscriptionHandler;
    private VerifyClientDeviceIdentityHandler verifyClientDeviceIdentityHandler;
    private Clock clock;
    private DomainEvents domainEvents;

    @BeforeEach
    void beforeEach() {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        domainEvents = new DomainEvents();
        metrics = new ClientDeviceAuthMetrics(clock);
        certificateSubscriptionHandler = new CertificateSubscriptionHandler(domainEvents, metrics);
        verifyClientDeviceIdentityHandler = new VerifyClientDeviceIdentityHandler(domainEvents, metrics);
        certificateSubscriptionHandler.listen();
        verifyClientDeviceIdentityHandler.listen();
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

        List<Metric> collectedMetrics = metrics.collectMetrics();
        Metric subscribeSuccess = collectedMetrics.get(0);

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

        List<Metric> collectedMetrics = metrics.collectMetrics();
        Metric subscribeFail = collectedMetrics.get(1);

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

        List<Metric> collectedMetrics = metrics.collectMetrics();
        Metric verifyDeviceIdentitySuccess = collectedMetrics.get(2);

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

        List<Metric> collectedMetrics = metrics.collectMetrics();
        Metric verifyDeviceIdentityFail = collectedMetrics.get(3);

        assertEquals(metric.getValue(), verifyDeviceIdentityFail.getValue());
        assertEquals(metric.getName(), verifyDeviceIdentityFail.getName());
        assertEquals(metric.getTimestamp(), verifyDeviceIdentityFail.getTimestamp());
        assertEquals(metric.getAggregation(), verifyDeviceIdentityFail.getAggregation());
        assertEquals(metric.getUnit(), verifyDeviceIdentityFail.getUnit());
        assertEquals(metric.getNamespace(), verifyDeviceIdentityFail.getNamespace());
    }

}
