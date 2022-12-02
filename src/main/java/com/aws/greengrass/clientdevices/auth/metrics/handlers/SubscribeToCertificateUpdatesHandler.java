/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics;
import com.aws.greengrass.clientdevices.auth.metrics.events.CertificateSubscriptionEvent;

import java.util.function.Consumer;
import javax.inject.Inject;

public class SubscribeToCertificateUpdatesHandler implements Consumer<CertificateSubscriptionEvent> {
    private final DomainEvents domainEvents;
    private final ClientDeviceAuthMetrics metrics;

    /**
     * Create handler for metric events.
     *
     * @param domainEvents Domain event router
     * @param metrics      Client Device Auth metrics
     */
    @Inject
    public SubscribeToCertificateUpdatesHandler(DomainEvents domainEvents, ClientDeviceAuthMetrics metrics) {
        this.domainEvents = domainEvents;
        this.metrics = metrics;
    }

    /**
     * Listen for metric events.
     */
    public void listen() {
        domainEvents.registerListener(this, CertificateSubscriptionEvent.class);
    }

    /**
     * Trigger metric update.
     *
     * @param event Update metric event
     */
    @Override
    public void accept(CertificateSubscriptionEvent event) {
        if (event.getCertificateType().equals(GetCertificateRequestOptions.CertificateType.SERVER)
                && event.isSuccessfulSubscription()) {
            metrics.subscribeSuccess();
        }
    }
}
