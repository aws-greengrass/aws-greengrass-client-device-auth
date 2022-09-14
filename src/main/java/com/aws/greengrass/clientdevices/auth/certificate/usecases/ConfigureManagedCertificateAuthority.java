/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.UseCaseException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import javax.inject.Inject;

/**
 * Creates or loads a  self-signed CA that will be used to issue certificates to other plugins and to verify client
 * devices.
 */
public class ConfigureManagedCertificateAuthority implements UseCases.UseCase<Void, Void> {
    private final CertificateManager certificateManager;
    private static final Logger logger = LogManager.getLogger(ConfigureManagedCertificateAuthority.class);
    private final CDAConfiguration configuration;


    /**
     * Configure core certificate authority.
     * @param certificateManager  Certificate manager.
     * @param configuration       CDA Service configuration
     */
    @Inject
    public ConfigureManagedCertificateAuthority(CertificateManager certificateManager, CDAConfiguration configuration) {
        this.certificateManager = certificateManager;
        this.configuration = configuration;
    }

    @Override
    public Void apply(Void unused) throws UseCaseException {
        logger.info("Configuring Greengrass managed certificate authority.");

        try {
            // NOTE: Update doesn't really reflect what we are doing we and the store should just store objects
            // not have the logic of how to create them
            certificateManager.generateCA(configuration.getCaPassphrase(), configuration.getCaType());
            configuration.updateCAPassphrase(certificateManager.getCaPassPhrase());
            configuration.updateCACertificates(certificateManager.getCACertificates());
        } catch (KeyStoreException | CertificateEncodingException | IOException e) {
            throw new UseCaseException(e);
        }

        return null;
    }
}
