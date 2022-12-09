/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.iot.events.VerifyClientDeviceIdentityEvent;
import com.aws.greengrass.clientdevices.auth.metrics.ClientDeviceAuthMetrics;

import java.util.function.Consumer;
import javax.inject.Inject;

public class VerifyClientDeviceIdentityHandler implements Consumer<VerifyClientDeviceIdentityEvent> {
    private final DomainEvents domainEvents;
    private final ClientDeviceAuthMetrics metrics;

    /**
     * Create handler for VerifyClientDeviceIdentity metric events.
     *
     * @param domainEvents Domain event router
     * @param metrics      {@link ClientDeviceAuthMetrics}
     */
    @Inject
    public VerifyClientDeviceIdentityHandler(DomainEvents domainEvents, ClientDeviceAuthMetrics metrics) {
        this.domainEvents = domainEvents;
        this.metrics = metrics;
    }

    /**
     * Listen for metric events.
     */
    public void listen() {
        domainEvents.registerListener(this, VerifyClientDeviceIdentityEvent.class);
    }

    @Override
    public void accept(VerifyClientDeviceIdentityEvent event) {
        if (event.getStatus() == VerifyClientDeviceIdentityEvent.VerificationStatus.SUCCESS) {
            metrics.verifyDeviceIdentitySuccess();
        } else if (event.getStatus() == VerifyClientDeviceIdentityEvent.VerificationStatus.FAIL) {
            metrics.verifyDeviceIdentityFailure();
        }
    }
}
