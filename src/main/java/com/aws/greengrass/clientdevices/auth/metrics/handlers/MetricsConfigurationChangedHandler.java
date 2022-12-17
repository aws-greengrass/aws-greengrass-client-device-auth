/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.configuration.events.MetricsConfigurationChanged;
import com.aws.greengrass.clientdevices.auth.metrics.MetricsEmitter;

import java.util.function.Consumer;
import javax.inject.Inject;

public class MetricsConfigurationChangedHandler implements Consumer<MetricsConfigurationChanged> {
    private final DomainEvents domainEvents;
    private final MetricsEmitter metricsEmitter;

    /**
     * Constructor for Metrics Configuration Changed Handler.
     *
     * @param domainEvents   Domain event router
     * @param metricsEmitter Metrics emitter
     */
    @Inject
    public MetricsConfigurationChangedHandler(DomainEvents domainEvents, MetricsEmitter metricsEmitter) {
        this.domainEvents = domainEvents;
        this.metricsEmitter = metricsEmitter;
    }

    /**
     * Listen for changes to the metrics configuration.
     */
    public void listen() {
        domainEvents.registerListener(this, MetricsConfigurationChanged.class);
    }

    /**
     * Restart the Metrics Emitter to apply new configuration, or stop if metrics have been disabled.
     *
     * @param event Metric configuration changed event
     */
    @Override
    public void accept(MetricsConfigurationChanged event) {
        if (event.getConfiguration().isDisableMetrics()) {
            metricsEmitter.stop();
        } else {
            metricsEmitter.start(event.getConfiguration().getAggregatePeriod());
        }
    }
}
