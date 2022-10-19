/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.events.CAConfigurationChanged;
import com.aws.greengrass.clientdevices.auth.certificate.usecases.ConfigureCustomCertificateAuthority;
import com.aws.greengrass.clientdevices.auth.certificate.usecases.ConfigureManagedCertificateAuthority;
import com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.UseCaseException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.function.Consumer;
import javax.inject.Inject;

public class CAConfigurationChangedHandler implements Consumer<CAConfigurationChanged> {
    private final UseCases useCases;
    private final DomainEvents domainEvents;
    private final Logger logger = LogManager.getLogger(CAConfigurationChangedHandler.class);



    /**
     * Configure certificate authority.
     * @param useCases     Use cases.
     * @param domainEvents Domain event router.
     */
    @Inject
    public CAConfigurationChangedHandler(UseCases useCases, DomainEvents domainEvents) {
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
    public void accept(CAConfigurationChanged event)  {
        CAConfiguration configuration = event.getConfiguration();

        try {
            if (configuration.isUsingCustomCA()) {
                useCases.get(ConfigureCustomCertificateAuthority.class).apply(configuration);
            } else {
                useCases.get(ConfigureManagedCertificateAuthority.class).apply(configuration);
            }
        } catch (UseCaseException e) {
            logger.atError().cause(e).log("Failed to configure certificate authority");
        }
    }
}

