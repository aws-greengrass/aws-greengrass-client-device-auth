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
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ResourceNotFoundException;
import software.amazon.awssdk.services.greengrassv2data.model.ThrottlingException;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIoTCertificateAssociationRequest;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import javax.inject.Inject;

public interface IotAuthClient {
    Optional<String> getActiveCertificateId(String certificatePem);

    boolean isThingAttachedToCertificate(Thing thing, Certificate certificate);

    class Default implements IotAuthClient {
        private static final Logger logger = LogManager.getLogger(Default.class);
        private static final RetryUtils.RetryConfig SERVICE_EXCEPTION_RETRY_CONFIG =
                RetryUtils.RetryConfig.builder().initialRetryInterval(Duration.ofMillis(100)).maxAttempt(3)
                        .retryableExceptions(Arrays.asList(ThrottlingException.class, InternalServerException.class))
                        .build();
        public static final String CERTIFICATE_ID = "certificateId";
        public static final String THING_NAME = "thingName";

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
        public Optional<String> getActiveCertificateId(String certificatePem) {
            if (Utils.isEmpty(certificatePem)) {
                throw new IllegalArgumentException("Certificate PEM is empty");
            }

            VerifyClientDeviceIdentityRequest request =
                    VerifyClientDeviceIdentityRequest.builder().clientDeviceCertificate(certificatePem).build();
            try {
                VerifyClientDeviceIdentityResponse response = RetryUtils.runWithRetry(SERVICE_EXCEPTION_RETRY_CONFIG,
                        () -> clientFactory.getGreengrassV2DataClient().verifyClientDeviceIdentity(request),
                        "verify-client-device-identity", logger);
                String certificateId = response.clientDeviceCertificateId();
                logger.atInfo().kv(CERTIFICATE_ID, certificateId)
                        .log("Client device certificate is valid");
                return Optional.of(certificateId);
            } catch (InterruptedException e) {
                logger.atError().cause(e).log("Verify client device identity got interrupted");
                // interrupt the current thread so that higher-level interrupt handlers can take care of it
                Thread.currentThread().interrupt();
                throw new CloudServiceInteractionException(
                        "Failed to verify client device identity, process got interrupted", e);
            } catch (ValidationException | ResourceNotFoundException e) {
                logger.atWarn().cause(e)
                        .log("Certificate doesn't exist or isn't active");
                return Optional.empty();
            } catch (Exception e) {
                logger.atError().cause(e)
                        .log("Failed to verify client device identity with cloud. Check that the core device's IoT "
                                + "policy grants the greengrass:VerifyClientDeviceIdentity permission.");
                throw new CloudServiceInteractionException("Failed to verify client device identity", e);
            }
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        public boolean isThingAttachedToCertificate(Thing thing, Certificate certificate) {
            if (thing == null || Utils.isEmpty(thing.getThingName())) {
                throw new IllegalArgumentException("No thing name available to validate");
            }

            if (certificate == null || Utils.isEmpty(certificate.getIotCertificateId())) {
                throw new IllegalArgumentException("No IoT certificate ID available to validate");
            }

            VerifyClientDeviceIoTCertificateAssociationRequest request =
                    VerifyClientDeviceIoTCertificateAssociationRequest.builder()
                            .clientDeviceThingName(thing.getThingName())
                            .clientDeviceCertificateId(certificate.getIotCertificateId()).build();
            try {
                RetryUtils.runWithRetry(SERVICE_EXCEPTION_RETRY_CONFIG,
                        () -> clientFactory.getGreengrassV2DataClient()
                                .verifyClientDeviceIoTCertificateAssociation(request),
                        "verify-certificate-thing-association", logger);
                logger.atInfo().kv(THING_NAME, thing.getThingName())
                        .kv(CERTIFICATE_ID, certificate.getIotCertificateId())
                        .log("Thing is attached to certificate");
                return true;
            } catch (InterruptedException e) {
                logger.atWarn().cause(e).log("Verify certificate thing association got interrupted");
                // interrupt the current thread so that higher-level interrupt handlers can take care of it
                Thread.currentThread().interrupt();
                throw new CloudServiceInteractionException(
                        "Failed to verify certificate thing association, process got interrupted", e);
            } catch (ValidationException | ResourceNotFoundException e) {
                logger.atWarn().cause(e).kv(THING_NAME, thing.getThingName())
                        .kv(CERTIFICATE_ID, certificate.getIotCertificateId())
                        .log("Thing is not attached to certificate");
                return false;
            } catch (Exception e) {
                logger.atError().cause(e).kv(THING_NAME, thing.getThingName())
                        .kv(CERTIFICATE_ID, certificate.getIotCertificateId())
                        .log("Failed to verify certificate thing association. Check that the core device's IoT policy"
                                + " grants the greengrass:VerifyClientDeviceIoTCertificateAssociation permission.");
                throw new CloudServiceInteractionException(
                        String.format("Failed to verify certificate %s thing %s association",
                                certificate.getIotCertificateId(), thing.getThingName()), e);
            }
        }
    }
}
