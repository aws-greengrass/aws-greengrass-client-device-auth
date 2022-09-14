/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.listeners;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.events.CAConfigurationChanged;
import com.aws.greengrass.clientdevices.auth.certificate.usecases.ConfigureCustomCertificateAuthority;
import com.aws.greengrass.clientdevices.auth.certificate.usecases.ConfigureManagedCertificateAuthority;
import com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.UseCaseException;

import javax.inject.Inject;

public class CAConfigurationChangedListener implements DomainEvents.DomainEventListener<CAConfigurationChanged> {
    private final UseCases useCases;
    private final DomainEvents domainEvents;


    /**
     * Configure certificate authority.
     * @param useCases     Use cases.
     * @param domainEvents Domain event router.
     */
    @Inject
    public CAConfigurationChangedListener(UseCases useCases, DomainEvents domainEvents) {
        this.useCases = useCases;
        this.domainEvents = domainEvents;
    }

    /**
     * Listen for configuration certificateAuthority configuration changes.
     */
    public void listen() {
        domainEvents.registerListener(this, CAConfigurationChanged.class);
    }

    /**
     * Trigger certificate CA configuration use cases depending on what changed.
     *
     * @param event Certificate authority configuration change event
     */
    @Override
    public void handle(CAConfigurationChanged event)  {
        CAConfiguration caConfiguration = event.getConfiguration();

        try {
            if (caConfiguration.isUsingCustomCA()) {
                useCases.get(ConfigureCustomCertificateAuthority.class).apply(null);
            } else {
                useCases.get(ConfigureManagedCertificateAuthority.class).apply(null);
            }
        } catch (UseCaseException e) {
            // TODO: Failed to configure CA <--- Should service error (Maybe return a type that should represent
            //  is the service should be errored)
        }
    }
}

