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
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.CreateSessionCommand;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.clientdevices.auth.session.SessionImpl;

import java.util.Optional;
import javax.inject.Inject;

public class CreateIoTThingSession
        implements UseCases.UseCase<SessionImpl, CreateSessionCommand> {
    private final IotAuthClient iotAuthClient;
    private final NetworkState networkState;
    private final ThingRegistry thingRegistry;
    private final CertificateRegistry certificateRegistry;


    /**
     * Verify a certificate with IoT Core.
     *
     * @param iotAuthClient       IoT auth client
     * @param thingRegistry       Thing Registry
     * @param certificateRegistry  Certificate Registry
     * @param networkState        Network state
     */
    @Inject
    public CreateIoTThingSession(IotAuthClient iotAuthClient, ThingRegistry thingRegistry,
                                 CertificateRegistry certificateRegistry, NetworkState networkState) {
        this.iotAuthClient = iotAuthClient;
        this.thingRegistry = thingRegistry;
        this.certificateRegistry = certificateRegistry;
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
    public Result<SessionImpl> apply(CreateSessionCommand dto) throws AuthenticationException {
        String certificatePem = dto.getCertificatePem();
        String thingName = dto.getThingName();
        Optional<Certificate> certificate;

        try {
            certificate = certificateRegistry.getCertificateFromPem(certificatePem);

            if (!certificate.isPresent()) {
                throw new AuthenticationException("Certificate isn't active");
            }

            Thing thing = thingRegistry.getOrCreateThing(thingName);

            if (isNetworkUp() && verifyFromCloud(thing, certificate.get())) {
                return Result.ok(new SessionImpl(certificate.get(), thing));
            }

            if (verifyLocally(thing, certificate.get())) {
                return Result.ok(new SessionImpl(certificate.get(), thing));
            }

            return Result.error(new AuthenticationException("Failed to verify certificate with attached to thing"));
        } catch (CloudServiceInteractionException | InvalidCertificateException e) {
            throw new AuthenticationException("Failed to verify certificate with cloud", e);
        }
    }

}
