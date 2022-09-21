/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.events.CACertificateChainChanged;
import com.aws.greengrass.clientdevices.auth.certificate.usecases.RotateCertificates;

import java.util.function.Consumer;
import javax.inject.Inject;

public class RotateCertificatesHandler implements Consumer<CACertificateChainChanged> {
    private final UseCases useCases;
    private final DomainEvents domainEvents;


    /**
     * Register core certificate authority with Greengrass cloud.
     * @param useCases     Use cases.
     * @param domainEvents Domain event router.
     */
    @Inject
    public RotateCertificatesHandler(UseCases useCases, DomainEvents domainEvents) {
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
        useCases.get(RotateCertificates.class).apply("Certificate chain changed");
    }
}
