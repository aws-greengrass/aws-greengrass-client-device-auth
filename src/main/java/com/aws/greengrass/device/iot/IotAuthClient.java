/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.aws.greengrass.device.iot;

import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.RetryUtils;
import com.aws.greengrass.util.Utils;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIoTCertificateAssociationRequest;

import javax.inject.Inject;

import static com.aws.greengrass.device.ClientDevicesAuthService.SERVICE_EXCEPTION_RETRY_CONFIG;

public interface IotAuthClient {
    String getActiveCertificateId(String certificatePem);

    boolean isThingAttachedToCertificate(Thing thing, Certificate certificate);

    class Default implements IotAuthClient {
        private static final Logger logger = LogManager.getLogger(Default.class);

        private final GreengrassServiceClientFactory clientFactory;

        /**
         * Default IotAuthClient constructor.
         *
         * @param clientFactory greengrass cloud service client factory
         */
        @Inject
        Default(GreengrassServiceClientFactory clientFactory) {
            this.clientFactory = clientFactory;
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        public String getActiveCertificateId(String certificatePem) {
            if (Utils.isEmpty(certificatePem)) {
                throw new IllegalArgumentException("Certificate PEM is empty");
            }

            VerifyClientDeviceIdentityRequest request =
                    VerifyClientDeviceIdentityRequest.builder().clientDeviceCertificate(certificatePem).build();
            try {
                VerifyClientDeviceIdentityResponse response = RetryUtils.runWithRetry(SERVICE_EXCEPTION_RETRY_CONFIG,
                        () -> clientFactory.getGreengrassV2DataClient().verifyClientDeviceIdentity(request),
                        "verify-client-device-identity", logger);
                return response.clientDeviceCertificateId();
            } catch (InterruptedException e) {
                logger.atError().cause(e).log("Verify client device identity got interrupted");
                // interrupt the current thread so that higher-level interrupt handlers can take care of it
                Thread.currentThread().interrupt();
                throw new CloudServiceInteractionException(
                        "Failed to verify client device identity, process got interrupted", e);
            } catch (Exception e) {
                logger.atError().cause(e).kv("certificatePem", certificatePem)
                        .log("Failed to verify client device identity with cloud");
                throw new CloudServiceInteractionException("Failed to verify client device identity", e);
            }
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        public boolean isThingAttachedToCertificate(Thing thing, Certificate certificate) {
            String thingName = thing.getThingName();
            if (Utils.isEmpty(thingName)) {
                throw new IllegalArgumentException("No thing name available to validate");
            }

            String iotCertificateId = certificate.getIotCertificateId();
            if (Utils.isEmpty(iotCertificateId)) {
                throw new IllegalArgumentException("No IoT certificate ID available to validate");
            }

            VerifyClientDeviceIoTCertificateAssociationRequest request =
                    VerifyClientDeviceIoTCertificateAssociationRequest.builder().clientDeviceThingName(thingName)
                            .clientDeviceCertificateId(iotCertificateId).build();
            try {
                RetryUtils.runWithRetry(SERVICE_EXCEPTION_RETRY_CONFIG,
                        () -> clientFactory.getGreengrassV2DataClient()
                                .verifyClientDeviceIoTCertificateAssociation(request),
                        "verify-certificate-thing-association", logger);
                return true;
            } catch (InterruptedException e) {
                logger.atError().cause(e).log("Verify certificate thing association got interrupted");
                // interrupt the current thread so that higher-level interrupt handlers can take care of it
                Thread.currentThread().interrupt();
                throw new CloudServiceInteractionException(
                        "Failed to verify certificate thing association, process got interrupted", e);
            } catch (Exception e) {
                logger.atError().cause(e).kv("thingName", thingName).kv("certificateId", iotCertificateId)
                        .log("Failed to verify certificate thing association");
                throw new CloudServiceInteractionException(
                        String.format("Failed to verify certificate %s thing %s association", iotCertificateId,
                                thingName), e);
            }
        }
    }
}
