/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics;
import com.aws.greengrass.clientdevices.auth.session.events.SessionCreationEvent;

import java.util.function.Consumer;
import javax.inject.Inject;

public class SessionCreationEventHandler implements Consumer<SessionCreationEvent> {
    private final DomainEvents domainEvents;
    private final ClientDeviceAuthMetrics metrics;

    /**
     * Constructor for the Get Client Device Auth Token Metric Handler.
     *
     * @param domainEvents Domain event router
     * @param metrics      Client Device Auth Metrics
     */
    @Inject
    public SessionCreationEventHandler(DomainEvents domainEvents, ClientDeviceAuthMetrics metrics) {
        this.domainEvents = domainEvents;
        this.metrics = metrics;
    }

    /**
     * Listen for metric updates.
     */
    public void listen() {
        domainEvents.registerListener(this, SessionCreationEvent.class);
    }

    @Override
    public void accept(SessionCreationEvent event) {
        if (event.getSessionCreationStatus() == SessionCreationEvent.SessionCreationStatus.SUCCESS) {
            metrics.authTokenSuccess();
        } else if (event.getSessionCreationStatus() == SessionCreationEvent.SessionCreationStatus.FAILURE) {
            metrics.authTokenFailure();
        }
    }
}
