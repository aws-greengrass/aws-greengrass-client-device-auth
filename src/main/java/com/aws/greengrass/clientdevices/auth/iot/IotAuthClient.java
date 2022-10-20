/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.componentmanager.ClientConfigurationUtils;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.tes.LazyCredentialProvider;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.IotSdkClientFactory;
import com.aws.greengrass.util.RegionUtils;
import com.aws.greengrass.util.Utils;
import com.aws.greengrass.util.exceptions.InvalidEnvironmentStageException;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2ClientBuilder;
import software.amazon.awssdk.services.greengrassv2.model.AssociatedClientDevice;
import software.amazon.awssdk.services.greengrassv2.model.ListClientDevicesAssociatedWithCoreDeviceRequest;
import software.amazon.awssdk.services.greengrassv2.model.ListClientDevicesAssociatedWithCoreDeviceResponse;
import software.amazon.awssdk.services.greengrassv2data.model.ResourceNotFoundException;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIoTCertificateAssociationRequest;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

public interface IotAuthClient {
    Optional<String> getActiveCertificateId(String certificatePem);

    Optional<Certificate> getIotCertificate(String certificatePem) throws InvalidCertificateException;

    boolean isThingAttachedToCertificate(Thing thing, Certificate certificate);

    Stream<Thing> getThingsAssociatedWithCoreDevice();

    class Default implements IotAuthClient {
        private static final Logger logger = LogManager.getLogger(Default.class);
        private static final String CERTPEM_KEY = "certificatePem";

        private final GreengrassServiceClientFactory clientFactory;
        private final LazyCredentialProvider lazyCredentialProvider;

        /**
         * Default IotAuthClient constructor.
         *
         * @param clientFactory greengrass cloud service client factory
         */
        @Inject
        Default(GreengrassServiceClientFactory clientFactory, LazyCredentialProvider lazyCredentialProvider) {
            this.clientFactory = clientFactory;
            this.lazyCredentialProvider = lazyCredentialProvider;
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
            try {
                // We can ignore the response since it contains only the cert ID, which we directly compute
                clientFactory.getGreengrassV2DataClient().verifyClientDeviceIdentity(request);
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
                                + " grants the greengrass:VerifyClientDeviceIoTCertificateAssociation permission");
                throw new CloudServiceInteractionException(
                        String.format("Failed to verify certificate %s thing %s association",
                                certificate.getCertificateId(), thing.getThingName()), e);
            }
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        public Stream<Thing> getThingsAssociatedWithCoreDevice() {
            DeviceConfiguration configuration = clientFactory.getDeviceConfiguration();
            String thingName = Coerce.toString(configuration.getThingName());

            ListClientDevicesAssociatedWithCoreDeviceRequest request =
                    ListClientDevicesAssociatedWithCoreDeviceRequest.builder()
                            .coreDeviceThingName(thingName)
                            .build();

            try (GreengrassV2Client client = getGGV2Client(configuration)) {
                ListClientDevicesAssociatedWithCoreDeviceResponse response =
                        client.listClientDevicesAssociatedWithCoreDevice(request);

                return response.associatedClientDevices().stream()
                        .map(AssociatedClientDevice::thingName)
                        .map(Thing::of);
            } catch (Exception e) {
                logger.atError().cause(e).kv("thing", thingName).log(
                        "Failed to list client devices associated to the core. Check that the core device's IoT"
                                + "policy grants the greengrass:ListClientDevicesAssociatedWithCoreDevice permission");
                throw new CloudServiceInteractionException(
                        String.format("Failed to list things attached to core %s", thingName), e);
            }
        }

        // TODO: This should not live here ideally it should be returned by the clientFactory but we
        //  are adding it here to avoid introducing new changes to the nucleus
        private GreengrassV2Client getGGV2Client(DeviceConfiguration deviceConfiguration)  {
            ApacheHttpClient.Builder httpClient = ClientConfigurationUtils
                    .getConfiguredClientBuilder(deviceConfiguration);

            String awsRegion = Coerce.toString(deviceConfiguration.getAWSRegion());
            GreengrassV2ClientBuilder clientBuilder = GreengrassV2Client.builder()
                    .httpClient(httpClient.build())
                    .credentialsProvider(lazyCredentialProvider)
                    .overrideConfiguration(
                            ClientOverrideConfiguration.builder().retryPolicy(RetryMode.STANDARD).build());

            if (Utils.isEmpty(awsRegion)) {
                return clientBuilder.build();
            }

            clientBuilder.region(Region.of(awsRegion));

            try {
                String environment = Coerce.toString(deviceConfiguration.getEnvironmentStage());
                String greengrassServiceEndpoint = RegionUtils.getGreengrassControlPlaneEndpoint(
                        awsRegion, IotSdkClientFactory.EnvironmentStage.fromString(environment));
                clientBuilder.endpointOverride(URI.create(greengrassServiceEndpoint));
            } catch (InvalidEnvironmentStageException e) {
                logger.atError().cause(e).log("Failed to configure greengrass service endpoint");
            }

            return clientBuilder.build();
        }
    }
}
