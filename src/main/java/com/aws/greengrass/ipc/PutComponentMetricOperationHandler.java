/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.ipc;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.telemetry.impl.Metric;
import com.aws.greengrass.telemetry.impl.MetricFactory;
import com.aws.greengrass.telemetry.models.TelemetryAggregation;
import com.aws.greengrass.telemetry.models.TelemetryUnit;
import com.aws.greengrass.util.Utils;
import org.apache.commons.lang3.EnumUtils;
import software.amazon.awssdk.aws.greengrass.GeneratedAbstractPutComponentMetricOperationHandler;
import software.amazon.awssdk.aws.greengrass.model.InvalidArgumentsError;
import software.amazon.awssdk.aws.greengrass.model.PutComponentMetricRequest;
import software.amazon.awssdk.aws.greengrass.model.PutComponentMetricResponse;
import software.amazon.awssdk.aws.greengrass.model.ServiceError;
import software.amazon.awssdk.eventstreamrpc.OperationContinuationHandlerContext;
import software.amazon.awssdk.eventstreamrpc.model.EventStreamJsonMessage;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PutComponentMetricOperationHandler extends GeneratedAbstractPutComponentMetricOperationHandler {
    private static final Logger logger = LogManager.getLogger(PutComponentMetricOperationHandler.class);
    private static final String SERVICE_NAME = "ServiceName";
    private static final String NON_ALPHANUMERIC_REGEX = "[^A-Za-z0-9]";
    private final String serviceName;
    private final Map<String, MetricFactory> metricFactoryMap = new HashMap<>();

    public PutComponentMetricOperationHandler(OperationContinuationHandlerContext context) {
        super(context);
        serviceName = context.getAuthenticationData().getIdentityLabel();
    }

    @Override
    protected void onStreamClosed() {
        //NA
    }

    @Override
    public void handleStreamEvent(EventStreamJsonMessage streamRequestEvent) {
        //NA
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public PutComponentMetricResponse handleRequest(PutComponentMetricRequest request) {
        logger.atDebug().log("Received putComponentMetricRequest from component " + serviceName);

        String opName = this.getOperationModelContext().getOperationName();

        //validate - metric name length, value is non negative, etc
        List<software.amazon.awssdk.aws.greengrass.model.Metric> metricList = request.getMetrics();
        try {
            validateComponentMetricRequest(opName, serviceName, metricList);
        } catch (IllegalArgumentException e) {
            logger.atError().cause(e).log("Invalid component metric request from %s", serviceName);
            throw new InvalidArgumentsError(e.getMessage());
        }

        //Perform translations on metrics list
        try {
            final String namespace = serviceName;
            translateAndEmit(metricList, namespace);
        } catch (IllegalArgumentException e) {
            logger.atError().cause(e).log("Invalid component metric request from %s", serviceName);
            throw new InvalidArgumentsError(e.getMessage());
        } catch (Exception e) {
            logger.atError().cause(e).log("Error while emitting metrics from %s", serviceName);
            throw new ServiceError(e.getMessage());
        }

        return new PutComponentMetricResponse();
    }

    // Validate metric request - check name, unit and value arguments
    // Also authorize request against access control policy
    // throw IllegalArgumentException if request params are invalid
    // throw AuthorizationException if request params don't match access control policy
    private void validateComponentMetricRequest(String opName, String serviceName,
                                                List<software.amazon.awssdk.aws.greengrass.model.Metric> metrics) {
            //throws AuthorizationException {
        if (Utils.isEmpty(metrics)) {
            throw new IllegalArgumentException(
                    String.format("Null or Empty list of metrics found in PutComponentMetricRequest"));
        }
        for (software.amazon.awssdk.aws.greengrass.model.Metric metric : metrics) {
            if (Utils.isEmpty(metric.getName()) || metric.getName().getBytes(StandardCharsets.UTF_8).length > 32
                    || Utils.isEmpty(metric.getUnitAsString()) || metric.getValue() < 0) {
                throw new IllegalArgumentException(
                        String.format("Invalid argument found in PutComponentMetricRequest"));
            }
        }
    }

    // Translate request metrics to telemetry metrics and emit them
    private void translateAndEmit(List<software.amazon.awssdk.aws.greengrass.model.Metric> componentMetrics,
                                  String metricNamespace) {
        final MetricFactory metricFactory =
                metricFactoryMap.computeIfAbsent(metricNamespace, k -> new MetricFactory(metricNamespace));
        Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        long timestamp = Instant.now(clock).toEpochMilli();

        componentMetrics.forEach(metric -> {
            logger.atDebug().kv(SERVICE_NAME, serviceName)
                    .log("Translating component metric to Telemetry metric" + metric.getName());
            Metric telemetryMetric = getTelemetryMetric(metric, metricNamespace, timestamp);
            logger.atDebug().kv(SERVICE_NAME, serviceName)
                    .log("Publish Telemetry metric" + telemetryMetric.getName());
            metricFactory.putMetricData(telemetryMetric);
        });
    }

    // Creates telemetry metric object for given request metric
    private Metric getTelemetryMetric(software.amazon.awssdk.aws.greengrass.model.Metric metric,
                                      String metricNamespace, long timestamp) {
        return Metric.builder()
                .namespace(metricNamespace)
                .name(metric.getName())
                .unit(valueOfIgnoreCase(metric.getUnitAsString()))
                .aggregation(TelemetryAggregation.Sum)
                .value(metric.getValue())
                .timestamp(timestamp)
                .build();
    }

    // Translate unit from metric request to telemetry unit
    private TelemetryUnit valueOfIgnoreCase(String unitAsString) {
        if (unitAsString == null || unitAsString.isEmpty()) {
            throw new IllegalArgumentException("Invalid telemetry unit: Found null or empty value");
        }

        final String replacedString = String.join("", unitAsString.split(NON_ALPHANUMERIC_REGEX));
        TelemetryUnit telemetryEnum = EnumUtils.getEnumIgnoreCase(TelemetryUnit.class, replacedString);

        if (telemetryEnum == null || telemetryEnum.toString().isEmpty()) {
            throw new IllegalArgumentException("Invalid telemetry unit: No matching TelemetryUnit type found");
        }
        return telemetryEnum;
    }

}
