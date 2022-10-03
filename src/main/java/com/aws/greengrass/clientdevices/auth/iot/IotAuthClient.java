/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.Utils;
import software.amazon.awssdk.services.greengrassv2data.model.ResourceNotFoundException;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIoTCertificateAssociationRequest;

import java.util.Optional;
import javax.inject.Inject;

public interface IotAuthClient {
    Optional<String> getActiveCertificateId(String certificatePem);

    Certificate getIotCertificate(String certificatePem) throws InvalidCertificateException;

    boolean isThingAttachedToCertificate(Thing thing, Certificate certificate);

    class Default implements IotAuthClient {
        private static final Logger logger = LogManager.getLogger(Default.class);
        private static final String CERTPEM_KEY = "certificatePem";

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
                VerifyClientDeviceIdentityResponse response =
                        clientFactory.getGreengrassV2DataClient().verifyClientDeviceIdentity(request);
                return Optional.of(response.clientDeviceCertificateId());
            } catch (ValidationException | ResourceNotFoundException e) {
                logger.atWarn().cause(e).kv(CERTPEM_KEY, certificatePem)
                        .log("Certificate doesn't exist or isn't active");
                return Optional.empty();
            } catch (Exception e) {
                logger.atError().cause(e).kv(CERTPEM_KEY, certificatePem)
                        .log("Failed to verify client device identity with cloud. Check that the core device's IoT "
                                + "policy grants the greengrass:VerifyClientDeviceIdentity permission.");
                throw new CloudServiceInteractionException("Failed to verify client device identity", e);
            }
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        public Certificate getIotCertificate(String certificatePem) throws InvalidCertificateException {
            if (Utils.isEmpty(certificatePem)) {
                throw new IllegalArgumentException("Certificate PEM is empty");
            }

            // Throws InvalidCertificateException if we can't parse the certificate
            Certificate cert = Certificate.fromPem(certificatePem);

            VerifyClientDeviceIdentityRequest request =
                    VerifyClientDeviceIdentityRequest.builder().clientDeviceCertificate(certificatePem).build();
            try {
                // We can ignore the response since it contains only the cert ID, which we directly compute
                clientFactory.getGreengrassV2DataClient().verifyClientDeviceIdentity(request);
                cert.setStatus(Certificate.Status.ACTIVE);
            } catch (ValidationException | ResourceNotFoundException e) {
                logger.atWarn().cause(e).kv(CERTPEM_KEY, certificatePem)
                        .log("Certificate doesn't exist or isn't active");
                throw new InvalidCertificateException("Invalid IoT certificate", e);
            } catch (Exception e) {
                // TODO: don't log at error level for network failures
                logger.atError().cause(e).kv(CERTPEM_KEY, certificatePem)
                        .log("Failed to verify client device identity with cloud. Check that the core device's IoT "
                                + "policy grants the greengrass:VerifyClientDeviceIdentity permission.");
                cert.setStatus(Certificate.Status.UNKNOWN);
            }

            return cert;
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        public boolean isThingAttachedToCertificate(Thing thing, Certificate certificate) {
            if (thing == null || Utils.isEmpty(thing.getThingName())) {
                throw new IllegalArgumentException("No thing name available to validate");
            }

            if (certificate == null || Utils.isEmpty(certificate.getCertificateId())) {
                throw new IllegalArgumentException("No IoT certificate ID available to validate");
            }

            VerifyClientDeviceIoTCertificateAssociationRequest request =
                    VerifyClientDeviceIoTCertificateAssociationRequest.builder()
                            .clientDeviceThingName(thing.getThingName())
                            .clientDeviceCertificateId(certificate.getCertificateId()).build();
            try {
                clientFactory.getGreengrassV2DataClient().verifyClientDeviceIoTCertificateAssociation(request);
                logger.atDebug().kv("thingName", thing.getThingName())
                        .kv("certificateId", certificate.getCertificateId()).log("Thing is attached to certificate");
                return true;
            } catch (ValidationException | ResourceNotFoundException e) {
                logger.atDebug().cause(e).kv("thingName", thing.getThingName())
                        .kv("certificateId", certificate.getCertificateId())
                        .log("Thing is not attached to certificate");
                return false;
            } catch (Exception e) {
                logger.atError().cause(e).kv("thingName", thing.getThingName())
                        .kv("certificateId", certificate.getCertificateId())
                        .log("Failed to verify certificate thing association. Check that the core device's IoT policy"
                                + " grants the greengrass:VerifyClientDeviceIoTCertificateAssociation permission.");
                throw new CloudServiceInteractionException(
                        String.format("Failed to verify certificate %s thing %s association",
                                certificate.getCertificateId(), thing.getThingName()), e);
            }
        }
    }
}
