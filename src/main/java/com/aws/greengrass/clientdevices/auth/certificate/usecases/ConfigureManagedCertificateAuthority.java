/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.CAConfiguration;
import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
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
public class ConfigureManagedCertificateAuthority implements UseCases.UseCase<Void, CAConfiguration> {
    private final CertificateManager certificateManager;
    private static final Logger logger = LogManager.getLogger(ConfigureManagedCertificateAuthority.class);
    private final RuntimeConfiguration runtimeConfig;


    /**
     * Configure core certificate authority.
     *
     * @param certificateManager Certificate manager.
     * @param runtimeConfig      - The runtime configuration
     */
    @Inject
    public ConfigureManagedCertificateAuthority(CertificateManager certificateManager,
                                                RuntimeConfiguration runtimeConfig) {
        this.certificateManager = certificateManager;
        this.runtimeConfig = runtimeConfig;
    }

    @Override
    public Void apply(CAConfiguration configuration) throws UseCaseException {
        // TODO: We should not be passing the entire configuration just what changed. We are just doing it for
        //  its convenience but eventually syncing the runtime config can be its own use case triggered by events.

        // TODO: We need to synchronize the changes that configuration has on the state of the service. There is
        //  a possibility that 2 threads run different use cases and change the certificate authority concurrently
        //  causing potential race conditions

        logger.info("Configuring Greengrass managed certificate authority.");

        try {
            // NOTE: Update doesn't really reflect what we are doing we and the store should just store objects
            // not have the logic of how to create them
            certificateManager.generateCA(runtimeConfig.getCaPassphrase(), configuration.getCaType());
            runtimeConfig.updateCAPassphrase(certificateManager.getCaPassPhrase());
            runtimeConfig.updateCACertificates(certificateManager.getCACertificates());
        } catch (IOException | CertificateEncodingException | KeyStoreException e) {
            throw new UseCaseException("Failed to configure managed CA", e);
        }

        return null;
    }
}
