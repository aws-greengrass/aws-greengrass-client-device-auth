/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration;
import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
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
public class ConfigureCustomCertificateAuthority implements UseCases.UseCase<Void, CAConfiguration> {
    private static final Logger logger = LogManager.getLogger(ConfigureCustomCertificateAuthority.class);
    private final CertificateManager certificateManager;
    private final RuntimeConfiguration runtimeConfig;


    /**
     * Configure core certificate authority.
     *
     * @param certificateManager Instance of CertificateManager
     * @param runtimeConfig - The runtime configuration
     */
    @Inject
    public ConfigureCustomCertificateAuthority(
            CertificateManager certificateManager, RuntimeConfiguration runtimeConfig) {
        this.certificateManager = certificateManager;
        this.runtimeConfig = runtimeConfig;
    }

    @Override
    public Void apply(CAConfiguration configuration) throws UseCaseException {
        // TODO: We need to synchronize the changes that configuration has on the state of the service. There is
        //  a possibility that 2 threads run different use cases and change the certificate authority concurrently
        //  causing potential race conditions

        try {
            logger.info("Configuring custom certificate authority.");
            // NOTE: We will pull the configureCustomCA out of the certificate Manager to here
            certificateManager.configureCustomCA(configuration);
            runtimeConfig.updateCACertificates(certificateManager.getCACertificates());
        } catch (InvalidConfigurationException | InvalidCertificateAuthorityException | CertificateEncodingException
                 | KeyStoreException | IOException e) {
            throw new UseCaseException("Failed to configure custom CA", e);
        }

        return null;
    }
}
