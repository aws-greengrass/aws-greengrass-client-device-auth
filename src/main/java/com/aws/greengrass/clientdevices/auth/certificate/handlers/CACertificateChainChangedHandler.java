/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.events.CACertificateChainChanged;
import com.aws.greengrass.clientdevices.auth.certificate.usecases.RegisterCertificateAuthorityUseCase;
import com.aws.greengrass.clientdevices.auth.exception.UseCaseException;

import java.util.function.Consumer;
import javax.inject.Inject;

public class CACertificateChainChangedHandler implements Consumer<CACertificateChainChanged> {
    private final UseCases useCases;
    private final DomainEvents domainEvents;


    /**
     * Register core certificate authority with Greengrass cloud.
     *
     * @param useCases     Use cases.
     * @param domainEvents Domain event router.
     */
    @Inject
    public CACertificateChainChangedHandler(UseCases useCases, DomainEvents domainEvents) {
        this.useCases = useCases;
        this.domainEvents = domainEvents;
    }

    /**
     * Listen for certificate authority change events.
     */
    public void listen() {
        domainEvents.registerListener(this, CACertificateChainChanged.class);
    }

    /**
     * Trigger certificate authority registration use case.
     *
     * @param event Certificate authority change event
     */
    @Override
    public void accept(CACertificateChainChanged event) {
        try {
            useCases.get(RegisterCertificateAuthorityUseCase.class).apply(null);
        } catch (UseCaseException e) {
            // TODO: Move retry logic from the domain to here, based on the result returned from the useCase
        }
    }
}
