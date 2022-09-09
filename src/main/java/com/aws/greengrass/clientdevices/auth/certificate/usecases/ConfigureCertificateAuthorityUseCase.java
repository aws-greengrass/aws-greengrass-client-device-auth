/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.exception.InvalidConfigurationException;
import com.aws.greengrass.clientdevices.auth.exception.UseCaseException;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import javax.inject.Inject;

public class ConfigureCertificateAuthorityUseCase
        implements UseCases.UseCase<Void, CDAConfiguration, UseCaseException> {
    private final CertificateManager certificateManager;
    private final DeviceConfiguration deviceConfiguration;
    private static final Logger logger = LogManager.getLogger(ConfigureCertificateAuthorityUseCase.class);


    @Inject
    public ConfigureCertificateAuthorityUseCase(
            CertificateManager certificateManager,
            DeviceConfiguration deviceConfiguration) {
        this.certificateManager = certificateManager;
        this.deviceConfiguration = deviceConfiguration;
    }

    @Override
    public Void apply(CDAConfiguration configuration) throws UseCaseException {
        // NOTE: This is not the final shape of this useCase we are just taking the logic out from
        //  the ClientDeviceAuthService first.
        String thingName = Coerce.toString(deviceConfiguration.getThingName());

        try {
            if (configuration.isUsingCustomCA()) {
                certificateManager.configureCustomCA(configuration);
            } else {
                certificateManager.generateCA(configuration.getCaPassphrase(), configuration.getCaType());
                configuration.updateCAPassphrase(certificateManager.getCaPassPhrase());
            }

            // Upload the generated or provided CA certificates to the GG cloud and update config
            // NOTE: uploadCoreDeviceCAs should not block execution.
            certificateManager.uploadCoreDeviceCAs(thingName);
            configuration.updateCACertificates(certificateManager.getCACertificates());
        } catch (CloudServiceInteractionException e) {
            logger.atError().cause(e).kv("coreThingName", thingName)
                    .log("Unable to upload core CA certificates to the cloud");
        } catch (CertificateEncodingException | KeyStoreException | IOException | InvalidConfigurationException   e) {
            throw new UseCaseException(e);
        }

        return null;
    }
}
