/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;

public interface IotAuthClient {
    Optional<String> getActiveCertificateId(String certificatePem);

    boolean isThingAttachedToCertificate(Thing thing, Certificate certificate);

    void clearLocalAuthCache();

    class Default implements IotAuthClient {
        private static final Logger logger = LogManager.getLogger(Default.class);
        private static final RetryUtils.RetryConfig SERVICE_EXCEPTION_RETRY_CONFIG =
                RetryUtils.RetryConfig.builder().initialRetryInterval(Duration.ofMillis(100)).maxAttempt(3)
                        .retryableExceptions(Arrays.asList(ThrottlingException.class, InternalServerException.class))
                        .build();
        // TODO: clear local auth-cache as necessary
        // certificateHash (SHA-256 hash of certificatePem) is stored instead of actual certificatePem
        private static final Map<String, String> certificateHashToIdMap = new ConcurrentHashMap<>();
        private static final Map<String, List<String>> certificateIdToThingMap = new ConcurrentHashMap<>();

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

            // cache active IoT Certificate Ids locally to avoid multiple cloud requests;
            // mapping of certificateHash <-> IoT Certificate Id is cached;
            // certificateHash is SHA-256 hash of certificatePem (to avoid storing actual certificatePem)
            String certId =  getCertificateIdAvailableLocally(certificatePem)
                    .orElseGet(() -> fetchActiveCertificateId(certificatePem).orElse(null));

            if (certId != null) {
                attachCertificateIdToPem(certId, certificatePem);
                return Optional.of(certId);
            }
            return Optional.empty();
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

            if (isThingAttachedToCertificateLocally(thing.getThingName(),
                    certificate.getIotCertificateId())) {
                return true;
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
                logger.atDebug().kv("thingName", thing.getThingName())
                        .kv("certificateId", certificate.getIotCertificateId())
                        .log("Thing is attached to certificate");

                // cache thing <-> IoT Certificate Ids mapping locally to avoid multiple cloud requests;
                attachThingToCertificateLocally(thing.getThingName(), certificate.getIotCertificateId());
                return true;
            } catch (InterruptedException e) {
                logger.atWarn().cause(e).log("Verify certificate thing association got interrupted");
                // interrupt the current thread so that higher-level interrupt handlers can take care of it
                Thread.currentThread().interrupt();
                throw new CloudServiceInteractionException(
                        "Failed to verify certificate thing association, process got interrupted", e);
            } catch (ValidationException | ResourceNotFoundException e) {
                logger.atDebug().cause(e).kv("thingName", thing.getThingName())
                        .kv("certificateId", certificate.getIotCertificateId())
                        .log("Thing is not attached to certificate");
                return false;
            } catch (Exception e) {
                logger.atError().cause(e).kv("thingName", thing.getThingName())
                        .kv("certificateId", certificate.getIotCertificateId())
                        .log("Failed to verify certificate thing association. Check that the core device's IoT policy"
                                + " grants the greengrass:VerifyClientDeviceIoTCertificateAssociation permission.");
                throw new CloudServiceInteractionException(
                        String.format("Failed to verify certificate %s thing %s association",
                                certificate.getIotCertificateId(), thing.getThingName()), e);
            }
        }

        @Override
        public void clearLocalAuthCache() {
            certificateHashToIdMap.clear();
            certificateIdToThingMap.clear();
        }

        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        private Optional<String> fetchActiveCertificateId(String certificatePem) {
            VerifyClientDeviceIdentityRequest request =
                    VerifyClientDeviceIdentityRequest.builder().clientDeviceCertificate(certificatePem).build();
            try {
                VerifyClientDeviceIdentityResponse response = RetryUtils.runWithRetry(SERVICE_EXCEPTION_RETRY_CONFIG,
                        () -> clientFactory.getGreengrassV2DataClient().verifyClientDeviceIdentity(request),
                        "verify-client-device-identity", logger);
                return Optional.of(response.clientDeviceCertificateId());
            } catch (InterruptedException e) {
                logger.atError().cause(e).log("Verify client device identity got interrupted");
                // interrupt the current thread so that higher-level interrupt handlers can take care of it
                Thread.currentThread().interrupt();
                throw new CloudServiceInteractionException(
                        "Failed to verify client device identity, process got interrupted", e);
            } catch (ValidationException | ResourceNotFoundException e) {
                logger.atWarn().cause(e).kv("certificatePem", certificatePem)
                        .log("Certificate doesn't exist or isn't active");
                return Optional.empty();
            } catch (Exception e) {
                logger.atError().cause(e).kv("certificatePem", certificatePem)
                        .log("Failed to verify client device identity with cloud. Check that the core device's IoT "
                                + "policy grants the greengrass:VerifyClientDeviceIdentity permission.");
                throw new CloudServiceInteractionException("Failed to verify client device identity", e);
            }
        }

        private Optional<String> getCertificateIdAvailableLocally(String certificatePem) {
            try {
                String certHash = sha256Hash(certificatePem);
                if (certificateHashToIdMap.containsKey(certHash)) {
                    return Optional.of(certificateHashToIdMap.get(certHash));
                }
            } catch (NoSuchAlgorithmException e) {
                return Optional.empty();
            }
            return Optional.empty();
        }

        private void attachCertificateIdToPem(String certificateId, String certificatePem) {
            try {
                certificateHashToIdMap.put(sha256Hash(certificatePem), certificateId);
            } catch (NoSuchAlgorithmException e) {
                logger.atTrace().cause(e).log("Could not store CertificatePem to CertificateId mapping");
            }
        }

        private synchronized boolean isThingAttachedToCertificateLocally(String thingName, String certificateId) {
            return certificateIdToThingMap.containsKey(certificateId)
                    && certificateIdToThingMap.get(certificateId).stream().anyMatch(thing -> thing.equals(thingName));
        }

        private synchronized void attachThingToCertificateLocally(String thingName, String certificateId) {
            List<String> attachedThings = certificateIdToThingMap.getOrDefault(certificateId, new ArrayList<>());
            attachedThings.add(thingName);
            certificateIdToThingMap.put(certificateId, attachedThings);
        }

        private String sha256Hash(String text) throws NoSuchAlgorithmException {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(text.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
