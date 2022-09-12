/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.listeners;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.events.ConfiguredCertificateAuthorityEvent;
import com.aws.greengrass.clientdevices.auth.certificate.usecases.RegisterCertificateAuthorityUseCase;
import com.aws.greengrass.clientdevices.auth.exception.UseCaseException;

import javax.inject.Inject;

public class ConfiguredCertificateAuthorityEventListener
        implements DomainEvents.DomainEventListener<ConfiguredCertificateAuthorityEvent> {
    private final DomainEvents domainEvents;


    /**
     * Register core certificate authority with Greengrass cloud.
     * @param domainEvents Domain event router.
     */
    @Inject
    public ConfiguredCertificateAuthorityEventListener(DomainEvents domainEvents) {
        this.domainEvents = domainEvents;
    }

    /**
     * Listen for certificate authority change events.
     */
    public void listen() {
        domainEvents.registerListener(this, ConfiguredCertificateAuthorityEvent.class);
    }

    /**
     * Trigger certificate authority registration use case.
     *
     * @param event Certificate authority change event
     */
    @Override
    public void handle(ConfiguredCertificateAuthorityEvent event) {
        RegisterCertificateAuthorityUseCase useCase = UseCases.get(RegisterCertificateAuthorityUseCase.class);
        try {
            useCase.apply(null);
        } catch (UseCaseException e) {
            // TODO: Move retry logic from the domain to here
        }
    }
}
