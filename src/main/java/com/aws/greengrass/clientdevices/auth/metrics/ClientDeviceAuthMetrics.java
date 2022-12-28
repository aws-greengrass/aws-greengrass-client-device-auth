/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.impl.MetricFactory;
import com.aws.greengrass.telemetry.models.TelemetryAggregation;
import com.aws.greengrass.telemetry.models.TelemetryUnit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class ClientDeviceAuthMetrics {
    private final AtomicLong subscribeToCertificateUpdatesSuccess = new AtomicLong();
    private final AtomicLong subscribeToCertificateUpdatesFailure = new AtomicLong();
    private final AtomicLong verifyClientDeviceIdentitySuccess = new AtomicLong();
    private final AtomicLong verifyClientDeviceIdentityFailure = new AtomicLong();
    private final AtomicLong authorizeClientDeviceActionSuccess = new AtomicLong();
    private final AtomicLong authorizeClientDeviceActionFailure = new AtomicLong();
    private final AtomicLong getClientDeviceAuthTokenSuccess = new AtomicLong();
    private final AtomicLong getClientDeviceAuthTokenFailure = new AtomicLong();
    private final AtomicLong serviceError = new AtomicLong();
    private final MetricFactory mf = new MetricFactory(NAMESPACE);
    private final Clock clock;
    private static final String NAMESPACE = "aws.greengrass.clientdevices.Auth";
    static final String METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS =
            "SubscribeToCertificateUpdates.Success";
    static final String METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_FAILURE =
            "SubscribeToCertificateUpdates.Failure";
    static final String METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS =
            "VerifyClientDeviceIdentity.Success";
    static final String METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_FAILURE =
            "VerifyClientDeviceIdentity.Failure";
    static final String METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS =
            "AuthorizeClientDeviceActions.Success";
    static final String METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_FAILURE =
            "AuthorizeClientDeviceActions.Failure";
    static final String METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS =
            "GetClientDeviceAuthToken.Success";
    static final String METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_FAILURE =
            "GetClientDeviceAuthToken.Failure";
    static final String METRIC_SERVICE_ERROR =
            "ServiceError";

    /**
     * Constructor for Client Device Auth Metrics.
     *
     * @param clock Clock
     */
    @Inject
    public ClientDeviceAuthMetrics(Clock clock) {
        this.clock = clock;
    }

    /**
     * Emit metrics using Metric Factory.
     */
    public void emitMetrics() {
        // TODO need to call this function on a timer
        List<Metric> retrievedMetrics = collectMetrics();
        for (Metric retrievedMetric : retrievedMetrics) {
            mf.putMetricData(retrievedMetric);
        }
    }

    /**
     * Builds the CDA metrics.
     *
     * @return a list of {@link Metric}
     */
    public List<Metric> collectMetrics() {
        List<Metric> metricsList = new ArrayList<>();
        long timestamp = Instant.now(clock).toEpochMilli();

        Metric metric = Metric.builder()
                .namespace(NAMESPACE)
                .name(METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(subscribeToCertificateUpdatesSuccess.getAndSet(0L))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name(METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_FAILURE)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(subscribeToCertificateUpdatesFailure.getAndSet(0L))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name(METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(verifyClientDeviceIdentitySuccess.getAndSet(0L))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name(METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_FAILURE)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(verifyClientDeviceIdentityFailure.getAndSet(0L))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name(METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(authorizeClientDeviceActionSuccess.getAndSet(0L))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name(METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_FAILURE)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(authorizeClientDeviceActionFailure.getAndSet(0L))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name(METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(getClientDeviceAuthTokenSuccess.getAndSet(0L))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name(METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_FAILURE)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(getClientDeviceAuthTokenFailure.getAndSet(0L))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metric = Metric.builder()
                .namespace(NAMESPACE)
                .name(METRIC_SERVICE_ERROR)
                .unit(TelemetryUnit.Count)
                .aggregation(TelemetryAggregation.Sum)
                .value(serviceError.getAndSet(0L))
                .timestamp(timestamp)
                .build();
        metricsList.add(metric);

        metricsList = metricsList.stream()
                .filter(m -> !m.getValue().equals(0L))
                .collect(Collectors.toList());

        return metricsList;
    }

    /**
     * Increments the SubscribeToCertificateUpdates.Success metric.
     */
    public void subscribeSuccess() {
        subscribeToCertificateUpdatesSuccess.incrementAndGet();
    }

    /**
     * Increments the SubscribeToCertificateUpdates.Failure metric
     */
    public void subscribeFailure() {
        subscribeToCertificateUpdatesFailure.incrementAndGet();
    }

    /**
     * Increments the VerifyClientDeviceIdentity.Success metric.
     */
    public void verifyDeviceIdentitySuccess() {
        verifyClientDeviceIdentitySuccess.incrementAndGet();
    }

    /**
     * Increments the VerifyClientDeviceIdentity.Failure metric.
     */
    public void verifyDeviceIdentityFailure() {
        verifyClientDeviceIdentityFailure.incrementAndGet();
    }

    /**
     * Increments the AuthorizeClientDeviceAction.Success metric.
     */
    public void authorizeActionSuccess() {
        authorizeClientDeviceActionSuccess.incrementAndGet();
    }

    /**
     * Increments the AuthorizeClientDeviceAction.Failure metric.
     */
    public void authorizeActionFailure() {
        authorizeClientDeviceActionFailure.incrementAndGet();
    }

    /**
     * Increments the GetClientDeviceAuthToken.Success metric
     */
    public void authTokenSuccess() {
        getClientDeviceAuthTokenSuccess.incrementAndGet();
    }

    /**
     * Increments the GetClientDeviceAuthToken.Failure metric
     */
    public void authTokenFailure() {
        getClientDeviceAuthTokenFailure.incrementAndGet();
    }

    /**
     * Increments the ServiceError metric.
     */
    public void incrementServiceError() {
        serviceError.incrementAndGet();
    }
}
