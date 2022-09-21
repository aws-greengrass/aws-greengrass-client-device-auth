/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import com.aws.greengrass.clientdevices.auth.certificate.infra.CertificateGeneratorRegistry;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.exception.CertificateGenerationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.Set;
import javax.inject.Inject;

/**
 * Rotates the Certificates for all the registered subscribers. Other plugin component can use the ClientDevicesAuthApi
 * to subscribe to CA updates and get new certificates when those events happen. This use case just triggers the
 * rotation for all the registered subscribers.
 */
public class RotateCertificates implements UseCases.UseCase<Void, String> {
    private final CertificateGeneratorRegistry certificateGeneratorRegistry;
    private static final Logger logger = LogManager.getLogger(RotateCertificates.class);
    private final ConnectivityInformation connectivityInformation;


    @Inject
    public RotateCertificates(
            CertificateGeneratorRegistry certificateGeneratorRegistry,
            ConnectivityInformation connectivityInformation) {
        this.certificateGeneratorRegistry = certificateGeneratorRegistry;
        this.connectivityInformation = connectivityInformation;
    }

    @Override
    public Result apply(String rotationReason) {
        Set<CertificateGenerator> generators = certificateGeneratorRegistry.getCertificateGenerators();
        Result result = Result.ok();

        if (generators.isEmpty()) {
            logger.info("Not certificates to rotate, skipping");
            return result;
        }

        for (CertificateGenerator generator : generators) {
            try {
                generator.generateCertificate(connectivityInformation::getCachedHostAddresses, rotationReason);
            } catch (CertificateGenerationException e) {
               result = Result.warning(e);
            }
        }

        return result;
    }
}
