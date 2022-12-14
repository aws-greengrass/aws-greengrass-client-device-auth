/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.ServiceErrorEvent;
import com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.function.Consumer;
import javax.inject.Inject;

public class ServiceErrorEventHandler implements Consumer<ServiceErrorEvent> {
    private final DomainEvents domainEvents;
    private final ClientDeviceAuthMetrics metrics;
    private static final Logger logger = LogManager.getLogger(ServiceErrorEventHandler.class);

    /**
     * Create handler for service error events.
     *
     * @param domainEvents Domain event router
     * @param metrics      Client Device Auth metrics
     */
    @Inject
    public ServiceErrorEventHandler(DomainEvents domainEvents, ClientDeviceAuthMetrics metrics) {
        this.domainEvents = domainEvents;
        this.metrics = metrics;
    }

    /**
     * Listen for service error events.
     */
    public void listen() {
        domainEvents.registerListener(this, ServiceErrorEvent.class);
    }

    @Override
    public void accept(ServiceErrorEvent event) {
        logger.atError().cause(event.getException()).log(event.getErrorMessage());
        metrics.incrementServiceError();
    }
}
