/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.GetClientDeviceAuthTokenEvent;
import com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics;

import java.util.function.Consumer;
import javax.inject.Inject;

public class GetClientDeviceAuthTokenMetricHandler implements Consumer<GetClientDeviceAuthTokenEvent> {
    private final DomainEvents domainEvents;
    private final ClientDeviceAuthMetrics metrics;

    /**
     * Constructor for the Get Client Device Auth Token Metric Handler.
     *
     * @param domainEvents Domain event router
     * @param metrics      Client Device Auth Metrics
     */
    @Inject
    public GetClientDeviceAuthTokenMetricHandler(DomainEvents domainEvents, ClientDeviceAuthMetrics metrics) {
        this.domainEvents = domainEvents;
        this.metrics = metrics;
    }

    /**
     * Listen for metric updates.
     */
    public void listen() {
        domainEvents.registerListener(this, GetClientDeviceAuthTokenEvent.class);
    }

    @Override
    public void accept(GetClientDeviceAuthTokenEvent event) {
        if (event.getGetAuthTokenStatus() == GetClientDeviceAuthTokenEvent.GetAuthTokenStatus.SUCCESS) {
            metrics.getAuthTokenSuccess();
        } else if (event.getGetAuthTokenStatus() == GetClientDeviceAuthTokenEvent.GetAuthTokenStatus.FAILURE) {
            metrics.getAuthTokenFailure();
        }
    }
}
