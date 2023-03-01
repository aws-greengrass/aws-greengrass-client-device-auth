/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.events.CACertificateChainChanged;
import com.aws.greengrass.clientdevices.auth.connectivity.usecases.GetConnectivityInformationUseCase;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import javax.inject.Inject;

import static com.aws.greengrass.clientdevices.auth.connectivity.usecases.GetConnectivityInformationUseCase.LEGACY_GET_CACHED_HOST_ADDRESSES;


// TODO: This class is a pseudo handler that stores the state of subscriptions. We are injecting it into the certificate
//  manager for now to avoid having to refactor how we store the subscriptions. In the future this handler will listen
//  to multiple events, call a use case and not store state.
public class CertificateRotationHandler implements Consumer<CACertificateChainChanged> {
    private static final Logger logger = LogManager.getLogger(CertificateRotationHandler.class);
    private final GetConnectivityInformationUseCase connectivityInformation;

    private final Set<CertificateGenerator> monitoredCertificateGenerators = new CopyOnWriteArraySet<>();
    private final DomainEvents domainEvents;

    /**
     * Construct a new ConfigurationMonitor.
     *
     * @param connectivityInformation get connectivity information use case
     * @param domainEvents            domain events service
     */
    @Inject
    public CertificateRotationHandler(GetConnectivityInformationUseCase connectivityInformation,
                                      DomainEvents domainEvents) {
        this.connectivityInformation = connectivityInformation;
        this.domainEvents = domainEvents;
    }

    /**
     * Start ca configuration monitor.
     */
    public void listen() {
        domainEvents.registerListener(this, CACertificateChainChanged.class);
    }

    /**
     * Add cert generator ca configuration monitor.
     *
     * @param cg certificate generator
     */
    public void addToMonitor(CertificateGenerator cg) {
        monitoredCertificateGenerators.add(cg);
    }

    /**
     * Remove cert generator from ca configuration monitor.
     *
     * @param cg certificate generator
     */
    public void removeFromMonitor(CertificateGenerator cg) {
        monitoredCertificateGenerators.remove(cg);
    }

    @Override
    public void accept(CACertificateChainChanged event) {
        logger.debug("Received event {}", event.getName());

        if (monitoredCertificateGenerators.isEmpty()) {
            logger.debug("No certificates to rotate, skipping");
            return;
        }

        for (CertificateGenerator generator : monitoredCertificateGenerators) {
            try {
                generator.generateCertificate(
                        LEGACY_GET_CACHED_HOST_ADDRESSES.apply(connectivityInformation),
                        "Certificate Configuration Changed");
            } catch (CertificateGenerationException e) {
                logger.atError().cause(e).log("Failed to rotate server certificate");
            }
        }
    }
}

