/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.certificate.events.SecurityConfigurationChanged;
import com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.Thing;

import java.util.function.Consumer;
import javax.inject.Inject;

public class SecurityConfigurationChangedHandler implements Consumer<SecurityConfigurationChanged> {
    private final DomainEvents domainEvents;

    /**
     * Construct SecurityConfigurationChanged Handler.
     *
     * @param domainEvents Domain event router
     */
    @Inject
    public SecurityConfigurationChangedHandler(DomainEvents domainEvents) {
        this.domainEvents = domainEvents;
    }

    /**
     * Listen for Security configuration changes.
     */
    public void listen() {
        domainEvents.registerListener(this, SecurityConfigurationChanged.class);
    }

    /**
     * Handle Security configuration change.
     *
     * @param event Security configuration changed event
     */
    @Override
    public void accept(SecurityConfigurationChanged event)  {
        SecurityConfiguration configuration = event.getConfiguration();
        Certificate.updateMetadataTrustDurationMinutes(configuration.getClientDeviceTrustDurationMinutes());
        Thing.updateMetadataTrustDurationMinutes(configuration.getClientDeviceTrustDurationMinutes());
    }
}
