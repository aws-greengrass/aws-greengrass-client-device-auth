/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
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
public class ConfigureManagedCertificateAuthority implements UseCases.UseCase<Void, CDAConfiguration> {
    private final CertificateManager certificateManager;
    private static final Logger logger = LogManager.getLogger(ConfigureManagedCertificateAuthority.class);


    /**
     * Configure core certificate authority.
     * @param certificateManager  Certificate manager.
     */
    @Inject
    public ConfigureManagedCertificateAuthority(CertificateManager certificateManager) {
        this.certificateManager = certificateManager;
    }

    @Override
    public Result apply(CDAConfiguration configuration) {
        // TODO: We should not be passing the entire configuration just what changed. We are just doing it for
        //  its convenience but eventually syncing the runtime config can be its own use case triggered by events.

        // TODO: We need to synchronize the changes that configuration has on the state of the service. There is
        //  a possibility that 2 threads run different use cases and change the certificate authority concurrently
        //  causing potential race conditions

        logger.info("Configuring Greengrass managed certificate authority.");

        try {
            // NOTE: Update doesn't really reflect what we are doing we and the store should just store objects
            // not have the logic of how to create them
            certificateManager.generateCA(configuration.getCaPassphrase(), configuration.getCaType());
            configuration.updateCAPassphrase(certificateManager.getCaPassPhrase());
            configuration.updateCACertificates(certificateManager.getCACertificates());
        } catch (IOException | CertificateEncodingException | KeyStoreException e) {
            logger.atError().cause(e).log("Failed to configure managed CA");
            return Result.error(e);
        }

        return Result.ok();
    }
}
