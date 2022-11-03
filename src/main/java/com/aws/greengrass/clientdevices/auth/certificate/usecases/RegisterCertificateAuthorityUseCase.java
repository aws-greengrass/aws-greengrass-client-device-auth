/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.usecases;

import com.aws.greengrass.clientdevices.auth.CertificateManager;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.exception.UseCaseException;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import javax.inject.Inject;

public class RegisterCertificateAuthorityUseCase implements UseCases.UseCase<Void, Void> {
    private static final Logger logger = LogManager.getLogger(RegisterCertificateAuthorityUseCase.class);

    private final CertificateManager certificateManager;
    private final DeviceConfiguration deviceConfiguration;
    private final CertificateStore certificateStore;


    /**
     * Register core certificate authority with Greengrass cloud.
     *
     * @param certificateManager  Certificate manager.
     * @param certificateStore    Certificate store.
     * @param deviceConfiguration Greengrass device configuration.
     */
    @Inject
    public RegisterCertificateAuthorityUseCase(CertificateManager certificateManager, CertificateStore certificateStore,
                                               DeviceConfiguration deviceConfiguration) {
        this.certificateManager = certificateManager;
        this.deviceConfiguration = deviceConfiguration;
        this.certificateStore = certificateStore;
    }

    private Optional<X509Certificate> getCAToUpload() throws KeyStoreException {
        X509Certificate[] caChain = certificateStore.getCaCertificateChain();

        if (caChain == null || caChain.length < 1) {
            return Optional.empty();
        }

        return Optional.of(caChain[caChain.length - 1]);
    }

    @Override
    public Void apply(Void unused) throws UseCaseException {
        // NOTE: This is not the final shape of this useCase we are just taking the logic out from
        //  the ClientDeviceAuthService first.
        String thingName = Coerce.toString(deviceConfiguration.getThingName());

        try {
            Optional<X509Certificate> certificate = getCAToUpload();

            if (!certificate.isPresent()) {
                logger.warn("Didn't find any CA to upload");
                return null;
            }

            // Upload the generated or provided CA certificates to the GG cloud and update config
            // NOTE: uploadCoreDeviceCAs should not block execution.
            certificateManager.uploadCoreDeviceCAs(thingName, certificate.get());
            return null;
        } catch (CloudServiceInteractionException e) {
            logger.atError().cause(e).kv("coreThingName", thingName)
                    .log("Unable to upload core CA certificates to the cloud");
            throw new UseCaseException(e);
        } catch (DeviceConfigurationException e) {
            // TODO: This should be retried, but the customer likely needs to make configuration changes first
            // For now, we will log and give up. But eventually this can be added to a DLQ and retried when
            // DeviceConfiguration is updated.
            logger.atError().cause(e).log("Unable to upload core CA due to bad DeviceConfiguration. "
                    + "Please correct configuration problem and restart Greengrass. "
                    + "Failure to upload core CA may result in client devices being unable to "
                    + "authenticate Greengrass.");
            throw new UseCaseException(e);
        } catch (CertificateEncodingException | KeyStoreException | IOException e) {
            throw new UseCaseException(e);
        }
    }
}
