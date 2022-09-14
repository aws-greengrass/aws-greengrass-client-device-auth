/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.InvalidCertificateAuthorityException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.clientdevices.auth.exception.UseCaseException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import javax.inject.Inject;

/**
 * Registers a custom certificate authority with a private key and certificate provide through the configuration.
 * The CA is used to issue certificates to other Greengrass plugins and verify device access.
 */
public class ConfigureCustomCertificateAuthority implements UseCases.UseCase<Void, Void> {
    private static final Logger logger = LogManager.getLogger(ConfigureCustomCertificateAuthority.class);
    private final CertificateManager certificateManager;
    private final CDAConfiguration configuration;


    /**
     * Configure core certificate authority.
     *
     * @param certificateManager Instance of CertificateManager
     * @param configuration      CDA Service configuration
     */
    @Inject
    public ConfigureCustomCertificateAuthority(CertificateManager certificateManager, CDAConfiguration configuration) {
        this.certificateManager = certificateManager;
        this.configuration = configuration;
    }

    @Override
    public Void apply(Void unused) throws UseCaseException {
        try {
            logger.info("Configuring custom certificate authority.");
            // NOTE: We will pull the configureCustomCA out of the certificate Manager to here
            certificateManager.configureCustomCA(configuration);
            configuration.updateCACertificates(certificateManager.getCACertificates());
        } catch (InvalidConfigurationException | InvalidCertificateAuthorityException | CertificateEncodingException
                 | KeyStoreException | IOException e) {
            throw new UseCaseException(e);
        }

        return null;
    }
}
