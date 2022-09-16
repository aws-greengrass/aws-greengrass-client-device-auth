/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.InvalidCertificateAuthorityException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
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
public class ConfigureCustomCertificateAuthority implements UseCases.UseCase<Void, CDAConfiguration> {
    private static final Logger logger = LogManager.getLogger(ConfigureCustomCertificateAuthority.class);
    private final CertificateManager certificateManager;


    /**
     * Configure core certificate authority.
     *
     * @param certificateManager Instance of CertificateManager
     */
    @Inject
    public ConfigureCustomCertificateAuthority(CertificateManager certificateManager) {
        this.certificateManager = certificateManager;
    }

    @Override
    public Result apply(CDAConfiguration configuration) {
        // TODO: We should not be passing the entire configuration just what changed. We are just doing it for
        //  its convenience but eventually syncing the runtime config can be its own use case triggered by events.

        // TODO: We need to synchronize the changes that configuration has on the state of the service. There is
        //  a possibility that 2 threads run different use cases and change the certificate authority concurrently
        //  causing potential race conditions

        try {
            logger.info("Configuring custom certificate authority.");
            // NOTE: We will pull the configureCustomCA out of the certificate Manager to here
            certificateManager.configureCustomCA(configuration);
            configuration.updateCACertificates(certificateManager.getCACertificates());
        } catch (InvalidConfigurationException | InvalidCertificateAuthorityException | CertificateEncodingException
                 | KeyStoreException | IOException e) {
            logger.atError().cause(e).log("Failed to configure custom CA");
            return Result.error(e);
        }

        return Result.ok();
    }
}
