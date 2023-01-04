/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import software.amazon.awssdk.aws.greengrass.model.Metric;
import software.amazon.awssdk.aws.greengrass.model.MetricUnitType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
     * Builds the CDA metrics.
     *
     * @return a list of {@link Metric}
     */
    public List<Metric> collectMetrics() {
        List<Metric> metricsList = new ArrayList<>();

        metricsList.add(createMetric(
                METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_SUCCESS,
                MetricUnitType.COUNT, subscribeToCertificateUpdatesSuccess.doubleValue()));

        metricsList.add(createMetric(
                METRIC_SUBSCRIBE_TO_CERTIFICATE_UPDATES_FAILURE,
                MetricUnitType.COUNT, subscribeToCertificateUpdatesFailure.doubleValue()
        ));

        metricsList.add(createMetric(
                METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_SUCCESS,
                MetricUnitType.COUNT, authorizeClientDeviceActionSuccess.doubleValue()
        ));

        metricsList.add(createMetric(
                METRIC_AUTHORIZE_CLIENT_DEVICE_ACTIONS_FAILURE,
                MetricUnitType.COUNT, authorizeClientDeviceActionFailure.doubleValue()
        ));

        metricsList.add(createMetric(
                METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_SUCCESS,
                MetricUnitType.COUNT, verifyClientDeviceIdentitySuccess.doubleValue()
        ));

        metricsList.add(createMetric(
                METRIC_VERIFY_CLIENT_DEVICE_IDENTITY_FAILURE,
                MetricUnitType.COUNT, verifyClientDeviceIdentityFailure.doubleValue()
        ));

        metricsList.add(createMetric(
                METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_SUCCESS,
                MetricUnitType.COUNT, getClientDeviceAuthTokenSuccess.doubleValue()
        ));

        metricsList.add(createMetric(
                METRIC_GET_CLIENT_DEVICE_AUTH_TOKEN_FAILURE,
                MetricUnitType.COUNT, getClientDeviceAuthTokenFailure.doubleValue()
        ));

        metricsList.add(createMetric(
                METRIC_SERVICE_ERROR, MetricUnitType.COUNT, serviceError.doubleValue()
        ));

        return metricsList;
    }

    private Metric createMetric(String name, MetricUnitType unit, Double value) {
        Metric metric = new Metric();
        metric.setName(name);
        metric.setUnit(unit);
        metric.setValue(value);
        return metric;
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
