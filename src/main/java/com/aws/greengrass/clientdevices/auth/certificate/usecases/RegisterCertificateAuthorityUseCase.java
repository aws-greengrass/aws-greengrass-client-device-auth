/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import javax.inject.Inject;

public class RegisterCertificateAuthorityUseCase
        implements UseCases.UseCase<Exception, Void> {
    private static final Logger logger = LogManager.getLogger(RegisterCertificateAuthorityUseCase.class);

    private final CertificateManager certificateManager;
    private final DeviceConfiguration deviceConfiguration;


    /**
     * Register core certificate authority with Greengrass cloud.
     * @param certificateManager  Certificate manager.
     * @param deviceConfiguration Greengrass device configuration.
     */
    @Inject
    public RegisterCertificateAuthorityUseCase(
            CertificateManager certificateManager,
            DeviceConfiguration deviceConfiguration) {
        this.certificateManager = certificateManager;
        this.deviceConfiguration = deviceConfiguration;
    }

    @Override
    public Result apply(Void unused)  {
        // NOTE: This is not the final shape of this useCase we are just taking the logic out from
        //  the ClientDeviceAuthService first.
        String thingName = Coerce.toString(deviceConfiguration.getThingName());

        try {
            // Upload the generated or provided CA certificates to the GG cloud and update config
            // NOTE: uploadCoreDeviceCAs should not block execution.
            certificateManager.uploadCoreDeviceCAs(thingName);
            return Result.ok();
        } catch (CloudServiceInteractionException e) {
            logger.atError().cause(e).kv("coreThingName", thingName)
                    .log("Unable to upload core CA certificates to the cloud");
            return Result.warning(e);
        } catch (DeviceConfigurationException e) {
            // TODO: This should be retried, but the customer likely needs to make configuration changes first
            // For now, we will log and give up. But eventually this can be added to a DLQ and retried when
            // DeviceConfiguration is updated.
            logger.atError().cause(e)
                    .log("Unable to upload core CA due to bad DeviceConfiguration. "
                            + "Please correct configuration problem and restart Greengrass. "
                            + "Failure to upload core CA may result in client devices being unable to "
                            + "authenticate Greengrass.");
            return Result.warning(e);
        } catch (CertificateEncodingException | KeyStoreException | IOException e) {
            return Result.warning(e);
        }
    }
}
