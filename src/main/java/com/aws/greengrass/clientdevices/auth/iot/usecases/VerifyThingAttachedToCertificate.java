/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.infra.NetworkState;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.VerifyThingAttachedToCertificateDTO;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;

import javax.inject.Inject;


public class VerifyThingAttachedToCertificate
        implements UseCases.UseCase<Boolean, VerifyThingAttachedToCertificateDTO> {
    private final IotAuthClient iotAuthClient;
    private final NetworkState networkState;
    private final ThingRegistry thingRegistry;


    /**
     * Verify a certificate with IoT Core.
     *
     * @param iotAuthClient       IoT auth client
     * @param thingRegistry       Thing Registry
     * @param networkState        Network state
     */
    @Inject
    public VerifyThingAttachedToCertificate(IotAuthClient iotAuthClient, ThingRegistry thingRegistry,
                                  NetworkState networkState) {
        this.iotAuthClient = iotAuthClient;
        this.thingRegistry = thingRegistry;
        this.networkState = networkState;
    }

    private boolean verifyLocally(Thing thing, Certificate certificate) {
        return thing.isCertificateAttached(certificate.getCertificateId());
    }

    private boolean verifyFromCloud(Thing thing, Certificate certificate) {
        if (iotAuthClient.isThingAttachedToCertificate(thing, certificate)) {
            thing.attachCertificate(certificate.getCertificateId());
            thingRegistry.updateThing(thing);
            return true;
        }

        thing.detachCertificate(certificate.getCertificateId());
        thingRegistry.updateThing(thing);
        return false;
    }

    private boolean isNetworkUp() {
        return networkState.getConnectionStateFromMqtt() == NetworkState.ConnectionState.NETWORK_UP;
    }

    /**
     * Verifies if a certificate is attached to a thing. When the device is online it will try to verify
     * it from the cloud and update the local values in case the device goes offline. When offline, the assertion
     * will be based on the locally stored values.
     * @param dto - VerifyCertificateAttachedToThingDTO
     */
    @Override
    public Result<Boolean> apply(VerifyThingAttachedToCertificateDTO dto) throws AuthenticationException {
        Certificate certificate = dto.getCertificate();
        Thing thing = dto.getThing();

        try {
            if (isNetworkUp()) {
                return Result.ok(verifyFromCloud(thing, certificate));
            }

            return Result.ok(verifyLocally(thing, certificate));
        } catch (CloudServiceInteractionException e) {
            return Result.ok(false);
        }
    }
}
