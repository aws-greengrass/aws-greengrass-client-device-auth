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

import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;


public class VerifyThingAttachedToCertificate implements
        UseCases.UseCase<Boolean, VerifyThingAttachedToCertificateDTO> {

    private static final Logger logger = LogManager.getLogger(VerifyThingAttachedToCertificate.class);

    private final IotAuthClient iotAuthClient;
    private final NetworkStateProvider networkState;
    private final ThingRegistry thingRegistry;


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

    private boolean verifyLocally(Thing thing, String certificateId) throws LocalVerificationException {
        logger.atDebug().kv("thing", thing.getThingName()).kv("certificate", certificateId)
                .log("Network down, verifying thing attached to certificate locally");

        Optional<Thing.Attachment> attachment = thing.getAttachment(certificateId);
        if (!attachment.isPresent()) {
            return false;
        }

        if (!attachment.get().isTrusted()) {
            throw new LocalVerificationException("Certificate attachment not trusted anymore. "
                    + "attachedAt: " + attachment.get().getCreated()
                    + ", expiration: " + attachment.get().getExpiration());
        }

        return true;
    }

    private boolean verifyFromCloud(Thing thing, String certificateId) {
        logger.atDebug().kv("thing", thing.getThingName()).kv("certificate", certificateId)
                .log("Network up, verifying thing attached to certificate from cloud");

        if (iotAuthClient.isThingAttachedToCertificate(thing, certificateId)) {
            thing.attachCertificate(certificateId);
            thingRegistry.updateThing(thing);
            return true;
        }

        thing.detachCertificate(certificateId);
        thingRegistry.updateThing(thing);
        return false;
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
     */
    @Override
    public Boolean apply(VerifyThingAttachedToCertificateDTO dto) throws LocalVerificationException {
        Thing thing = thingRegistry.getThing(dto.getThingName());
        if (Objects.isNull(thing)) {
            throw new LocalVerificationException("Thing " + dto.getThingName() + " not in thing registry");
        }

        if (isNetworkUp()) {
            try {
                return verifyFromCloud(thing, dto.getCertificateId());
            } catch (CloudServiceInteractionException ignored) {
                // fallback to local verification
            }
        }

        return verifyLocally(thing, dto.getCertificateId());
    }
}
