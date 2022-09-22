/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.certificate.events.CAConfigurationChanged;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.inject.Inject;


// TODO: This class is acting almost like a handler the reason for not using a handler directly is because it registers
//  CertificateGenerators same as the CertExpiryMonitor and CISMonitor. In order to minimize the surface area of changes
//  without refactoring how we store subscribers we are taking this approach. Once we refactor how we store handlers
//  this should become a fully fledged handler.
public class CAConfigurationMonitor {
    private static final Logger logger = LogManager.getLogger(CAConfigurationMonitor.class);
    private final ConnectivityInformation connectivityInformation;

    private final Set<CertificateGenerator> monitoredCertificateGenerators = new CopyOnWriteArraySet<>();
    private final DomainEvents domainEvents;


    /**
     * Construct a new ConfigurationMonitor.
     *
     * @param connectivityInformation Connectivity Info Provider
     * @param domainEvents domain events service
     */
    @Inject
    public CAConfigurationMonitor(ConnectivityInformation connectivityInformation, DomainEvents domainEvents) {
        this.connectivityInformation = connectivityInformation;
        this.domainEvents = domainEvents;
    }

    /**
     * Start ca configuration monitor.
     */
    public void startMonitor() {
        domainEvents.registerListener(this::getHandler, CAConfigurationChanged.class);
    }

    private Result getHandler(CAConfigurationChanged event) {
        logger.debug("Received event {}", event.getName());

        if (monitoredCertificateGenerators.isEmpty()) {
            logger.info("No certificates to rotate, skipping");
            return Result.ok();
        }

        Result result = Result.ok();

        for (CertificateGenerator generator : monitoredCertificateGenerators) {
            try {
                generator.generateCertificate(
                        connectivityInformation::getCachedHostAddresses,
                        "Certificate Configuration Changed");
            } catch (CertificateGenerationException e) {
                result = Result.error(e);
            }
        }

        return result;
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
}
