/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.clientdevices.auth.exception.UseCaseException;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import javax.inject.Inject;

public class ConfigureCertificateAuthorityUseCase implements UseCases.UseCase<Void, Void, UseCaseException> {
    private final CertificateManager certificateManager;
    private final CDAConfiguration cdaConfiguration;


    /**
     * Configure core certificate authority.
     * @param certificateManager  Certificate manager.
     * @param cdaConfiguration    Client device auth configuration wrapper.
     */
    @Inject
    public ConfigureCertificateAuthorityUseCase(
            CertificateManager certificateManager,
            CDAConfiguration cdaConfiguration) {
        this.certificateManager = certificateManager;
        this.cdaConfiguration = cdaConfiguration;
    }

    @Override
    public Void apply(Void unused) throws UseCaseException {
        // NOTE: This is not the final shape of this useCase we are just taking the logic out from
        //  the ClientDeviceAuthService first.
        try {
            if (cdaConfiguration.isUsingCustomCA()) {
                certificateManager.configureCustomCA(cdaConfiguration);
            } else {
                certificateManager.generateCA(cdaConfiguration.getCaPassphrase(), cdaConfiguration.getCaType());
                cdaConfiguration.updateCAPassphrase(certificateManager.getCaPassPhrase());
            }

            cdaConfiguration.updateCACertificates(certificateManager.getCACertificates());
        } catch (KeyStoreException | InvalidConfigurationException | IOException | CertificateEncodingException e) {
            throw new UseCaseException(e);
        }

        // Register new certificate authority
        UseCases.get(RegisterCertificateAuthorityUseCase.class).apply(null);

        return null;
    }
}
