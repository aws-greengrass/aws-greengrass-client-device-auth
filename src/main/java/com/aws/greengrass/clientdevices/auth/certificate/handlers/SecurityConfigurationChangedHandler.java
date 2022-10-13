/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.events.SecurityConfigurationChanged;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.iot.usecases.VerifyMetadataTrust;

import java.util.function.Consumer;
import javax.inject.Inject;

public class SecurityConfigurationChangedHandler implements Consumer<SecurityConfigurationChanged> {
    private final DomainEvents domainEvents;
    private final UseCases useCases;

    /**
     * Construct SecurityConfigurationChanged Handler.
     *
     * @param domainEvents Domain event router
     * @param useCases Use cases
     */
    @Inject
    public SecurityConfigurationChangedHandler(DomainEvents domainEvents, UseCases useCases) {
        this.domainEvents = domainEvents;
        this.useCases = useCases;
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
        CDAConfiguration configuration = event.getConfiguration();
        useCases.get(VerifyMetadataTrust.class)
                .updateClientDeviceTrustDurationHours(configuration.getClientDeviceTrustDurationHours());
    }
}
