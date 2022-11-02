/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Utils;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.model.AssociatedClientDevice;
import software.amazon.awssdk.services.greengrassv2.model.ListClientDevicesAssociatedWithCoreDeviceRequest;
import software.amazon.awssdk.services.greengrassv2.model.ListClientDevicesAssociatedWithCoreDeviceResponse;
import software.amazon.awssdk.services.greengrassv2.paginators.ListClientDevicesAssociatedWithCoreDeviceIterable;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.ResourceNotFoundException;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIoTCertificateAssociationRequest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

public interface IotAuthClient {
    Optional<String> getActiveCertificateId(String certificatePem);

    Optional<Certificate> getIotCertificate(String certificatePem) throws InvalidCertificateException;

    boolean isThingAttachedToCertificate(Thing thing, Certificate certificate);

    boolean isThingAttachedToCertificate(Thing thing, String certificateId);


    Stream<List<AssociatedClientDevice>> getThingsAssociatedWithCoreDevice();

    class Default implements IotAuthClient {
        private static final Logger logger = LogManager.getLogger(Default.class);
        private static final String CERTPEM_KEY = "certificatePem";

        private final GreengrassClientFactory clientFactory;

        /**
         * Default IotAuthClient constructor.
         *
         * @param clientFactory greengrass cloud service client factory
         */
        @Inject
        Default(GreengrassClientFactory clientFactory) {
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
            try (GreengrassV2DataClient client = clientFactory.getGGV2DataClient()) {
                VerifyClientDeviceIdentityResponse response = client.verifyClientDeviceIdentity(request);
                return Optional.of(response.clientDeviceCertificateId());
            } catch (ValidationException | ResourceNotFoundException e) {
                logger.atWarn().cause(e).kv(CERTPEM_KEY, certificatePem)
                        .log("Certificate doesn't exist or isn't active");
                return Optional.empty();
            } catch (Exception e) {
                logger.atError().cause(e).kv(CERTPEM_KEY, certificatePem)
                        .log("Failed to verify client device identity with cloud. Check that the core device's IoT "
                                + "policy grants the greengrass:VerifyClientDeviceIdentity permission");
                throw new CloudServiceInteractionException("Failed to verify client device identity", e);
            }
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        public Optional<Certificate> getIotCertificate(String certificatePem) throws InvalidCertificateException {
            // Throws InvalidCertificateException if we can't parse the certificate
            Certificate cert = Certificate.fromPem(certificatePem);

            VerifyClientDeviceIdentityRequest request =
                    VerifyClientDeviceIdentityRequest.builder().clientDeviceCertificate(certificatePem).build();
            try (GreengrassV2DataClient client = clientFactory.getGGV2DataClient()) {
                // We can ignore the response since it contains only the cert ID, which we directly compute
                client.verifyClientDeviceIdentity(request);
                cert.setStatus(Certificate.Status.ACTIVE);
            } catch (ValidationException | ResourceNotFoundException e) {
                logger.atWarn().cause(e).kv(CERTPEM_KEY, certificatePem)
                        .log("Certificate doesn't exist or isn't active");
                cert.setStatus(Certificate.Status.UNKNOWN);
            } catch (Exception e) {
                // TODO: don't log at error level for network failures
                logger.atError().cause(e).kv(CERTPEM_KEY, certificatePem)
                        .log("Failed to verify client device identity with cloud. Check that the core device's IoT "
                                + "policy grants the greengrass:VerifyClientDeviceIdentity permission");
                return Optional.empty();
            }

            return Optional.of(cert);
        }

        @Override
        public boolean isThingAttachedToCertificate(Thing thing, Certificate certificate) {
            if (Objects.isNull(certificate)) {
                return false;
            }
            return isThingAttachedToCertificate(thing, certificate.getCertificateId());
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        public boolean isThingAttachedToCertificate(Thing thing, String certificateId) {
            if (thing == null || Utils.isEmpty(thing.getThingName())) {
                throw new IllegalArgumentException("No thing name available to validate");
            }

            if (certificateId == null || Utils.isEmpty(certificateId)) {
                throw new IllegalArgumentException("No IoT certificate ID available to validate");
            }

            VerifyClientDeviceIoTCertificateAssociationRequest request =
                    VerifyClientDeviceIoTCertificateAssociationRequest.builder()
                            .clientDeviceThingName(thing.getThingName())
                            .clientDeviceCertificateId(certificateId).build();
            try (GreengrassV2DataClient client = clientFactory.getGGV2DataClient()) {
                client.verifyClientDeviceIoTCertificateAssociation(request);
                logger.atDebug().kv("thingName", thing.getThingName())
                        .kv("certificateId", certificateId).log("Thing is attached to certificate");
                return true;
            } catch (ValidationException | ResourceNotFoundException e) {
                logger.atDebug().cause(e).kv("thingName", thing.getThingName())
                        .kv("certificateId", certificateId)
                        .log("Thing is not attached to certificate");
                return false;
            } catch (Exception e) {
                logger.atError().cause(e).kv("thingName", thing.getThingName())
                        .kv("certificateId", certificateId)
                        .log("Failed to verify certificate thing association. Check that the core device's IoT policy"
                                + " grants the greengrass:VerifyClientDeviceIoTCertificateAssociation permission");
                throw new CloudServiceInteractionException(
                        String.format("Failed to verify certificate %s thing %s association",
                                certificateId, thing.getThingName()), e);
            }
        }

        @Override
        public Stream<List<AssociatedClientDevice>> getThingsAssociatedWithCoreDevice() {
            DeviceConfiguration configuration = clientFactory.getDeviceConfiguration();
            String thingName = Coerce.toString(configuration.getThingName());

            ListClientDevicesAssociatedWithCoreDeviceRequest request =
                    ListClientDevicesAssociatedWithCoreDeviceRequest.builder()
                            .coreDeviceThingName(thingName)
                            .build();

            try (GreengrassV2Client client = clientFactory.getGGV2Client()) {
                ListClientDevicesAssociatedWithCoreDeviceIterable responses =
                        client.listClientDevicesAssociatedWithCoreDevicePaginator(request);

                return responses.stream()
                        .map(ListClientDevicesAssociatedWithCoreDeviceResponse::associatedClientDevices);
            }
        }
    }
}
