/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.VerifyThingAttachedToCertificateDTO;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;


public class VerifyThingAttachedToCertificate
        implements UseCases.UseCase<VerifyThingAttachedToCertificate.Result, VerifyThingAttachedToCertificateDTO> {
    private final IotAuthClient iotAuthClient;
    private final NetworkStateProvider networkState;
    private final ThingRegistry thingRegistry;
    private static final Logger logger = LogManager.getLogger(VerifyThingAttachedToCertificate.class);


    /**
     * Verify a certificate with IoT Core.
     *
     * @param iotAuthClient IoT auth client
     * @param thingRegistry Thing Registry
     * @param networkState  Network state
     */
    @Inject
    public VerifyThingAttachedToCertificate(IotAuthClient iotAuthClient, ThingRegistry thingRegistry,
                                            NetworkStateProvider networkState) {
        this.iotAuthClient = iotAuthClient;
        this.thingRegistry = thingRegistry;
        this.networkState = networkState;
    }

    private Result verifyLocally(Thing thing, String certificateId) {
        logger.atDebug().kv("thing", thing.getThingName()).kv("certificate", certificateId)
                .log("Network down, verifying thing attached to certificate locally");

        Optional<Instant> lastAttachedOn = thing.certificateLastAttachedOn(certificateId);

        return Result.builder()
                .thingHasValidAttachmentToCertificate(
                        lastAttachedOn.map(thing::isCertAttachmentTrusted).orElse(false))
                .lastAttached(lastAttachedOn.orElse(null))
                .attachmentExpiration(lastAttachedOn.map(thing::getAttachmentExpiration).orElse(null))
                .verificationSource(Result.VerificationSource.LOCAL)
                .build();
    }

    private Result verifyFromCloud(Thing thing, String certificateId) throws CloudServiceInteractionException {
        logger.atDebug().kv("thing", thing.getThingName()).kv("certificate", certificateId)
                .log("Network up, verifying thing attached to certificate from cloud");

        if (iotAuthClient.isThingAttachedToCertificate(thing, certificateId)) {
            thing.attachCertificate(certificateId);
            thingRegistry.updateThing(thing);
            Optional<Instant> lastAttached = thing.certificateLastAttachedOn(certificateId);
            return Result.builder()
                    .thingHasValidAttachmentToCertificate(true)
                    .lastAttached(lastAttached.orElse(null))
                    .attachmentExpiration(lastAttached.map(thing::getAttachmentExpiration).orElse(null))
                    .verificationSource(Result.VerificationSource.CLOUD)
                    .build();
        }

        thing.detachCertificate(certificateId);
        thingRegistry.updateThing(thing);
        return Result.builder()
                .thingHasValidAttachmentToCertificate(false)
                .verificationSource(Result.VerificationSource.CLOUD)
                .build();
    }

    private boolean isNetworkUp() {
        return networkState.getConnectionState() == NetworkStateProvider.ConnectionState.NETWORK_UP;
    }

    /**
     * Verifies if a certificate is attached to a thing. When the device is online it will try to verify it from the
     * cloud and update the local values in case the device goes offline. When offline, the assertion will be based on
     * the locally stored values.
     *
     * @param dto - VerifyCertificateAttachedToThingDTO
     * @return  verification result
     */
    @Override
    public Result apply(VerifyThingAttachedToCertificateDTO dto) {
        Thing thing = thingRegistry.getThing(dto.getThingName());
        if (Objects.isNull(thing)) {
            return Result.builder()
                    .thingHasValidAttachmentToCertificate(false)
                    .verificationSource(Result.VerificationSource.LOCAL)
                    .build();
        }

        try {
            if (isNetworkUp()) {
                return verifyFromCloud(thing, dto.getCertificateId());
            }
            return verifyLocally(thing, dto.getCertificateId());
        } catch (CloudServiceInteractionException e) {
            return verifyLocally(thing, dto.getCertificateId());
        }
    }

    @Value
    @Builder
    public static class Result {
        boolean thingHasValidAttachmentToCertificate;
        Instant lastAttached;
        Instant attachmentExpiration;
        VerificationSource verificationSource;

        enum VerificationSource {
            CLOUD, LOCAL
        }
    }
}
