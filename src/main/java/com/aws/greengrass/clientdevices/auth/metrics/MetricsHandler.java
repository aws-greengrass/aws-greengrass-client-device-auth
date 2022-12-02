/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;

import java.util.function.Consumer;
import javax.inject.Inject;

public class MetricsHandler implements Consumer<MetricEvent> {
    private final DomainEvents domainEvents;
    private final ClientDeviceAuthMetrics metrics;

    /**
     * Create handler for metric events.
     *
     * @param domainEvents Domain event router
     * @param metrics      Client Device Auth metrics
     */
    @Inject
    public MetricsHandler(DomainEvents domainEvents, ClientDeviceAuthMetrics metrics) {
        this.domainEvents = domainEvents;
        this.metrics = metrics;
    }

    /**
     * Listen for metric events.
     */
    public void listen() {
        domainEvents.registerListener(this, MetricEvent.class);
    }

    /**
     * Trigger metric update.
     *
     * @param event Update metric event
     */
    @Override
    public void accept(MetricEvent event) {
        switch (event.getMetricName()) {
            //more cases will be added as more metrics are added
            case "SubscribeToCertificateUpdates.Success":
                metrics.subscribeSuccess();
                break;
            case "SubscribeToCertificateUpdates.Failure":
                //implementation pending
                break;
            default:
                break;
        }
    }
}
