/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics.handlers;

import com.aws.greengrass.clientdevices.auth.api.AuthorizeClientDeviceActionEvent;
import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics;

import java.util.function.Consumer;
import javax.inject.Inject;

public class AuthorizeClientDeviceActionsMetricHandler implements Consumer<AuthorizeClientDeviceActionEvent> {
    private final DomainEvents domainEvents;
    private final ClientDeviceAuthMetrics metrics;

    /**
     * Create metric handler for the Authorize Client Device Actions API
     *
     * @param domainEvents Domain event router
     * @param metrics      Client Device Auth metrics
     */
    @Inject
    public AuthorizeClientDeviceActionsMetricHandler(DomainEvents domainEvents, ClientDeviceAuthMetrics metrics) {
        this.domainEvents = domainEvents;
        this.metrics = metrics;
    }

    /**
     * Listen for metric events.
     */
    public void listen() {
        domainEvents.registerListener(this, AuthorizeClientDeviceActionEvent.class);
    }

    @Override
    public void accept(AuthorizeClientDeviceActionEvent event) {
        if (event.getStatus() == AuthorizeClientDeviceActionEvent.AuthorizationStatus.SUCCESS) {
            metrics.authorizeActionSuccess();
        } else if (event.getStatus() == AuthorizeClientDeviceActionEvent.AuthorizationStatus.FAIL) {
            metrics.authorizeActionFailure();
        }
    }
}
