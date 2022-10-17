/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.CreateSessionDTO;
import com.aws.greengrass.clientdevices.auth.iot.dto.VerifyThingAttachedToCertificateDTO;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.clientdevices.auth.session.SessionImpl;

import java.util.Optional;
import javax.inject.Inject;

public class CreateIoTThingSession
        implements UseCases.UseCase<SessionImpl, CreateSessionDTO> {
    private final ThingRegistry thingRegistry;
    private final CertificateRegistry certificateRegistry;
    private final UseCases useCases;


    /**
     * Verify a certificate with IoT Core.
     *
     * @param thingRegistry       Thing Registry
     * @param certificateRegistry  Certificate Registry
     * @param useCases  UseCases service
     */
    @Inject
    public CreateIoTThingSession(ThingRegistry thingRegistry,
                                 CertificateRegistry certificateRegistry, UseCases useCases) {
        this.thingRegistry = thingRegistry;
        this.certificateRegistry = certificateRegistry;
        this.useCases = useCases;
    }


    /**
     * Creates an IoT session if the thing is attached to an active certificate.
     * @param dto - VerifyCertificateAttachedToThingDTO
     */
    @Override
    public Result<SessionImpl> apply(CreateSessionDTO dto) throws AuthenticationException {
        String certificatePem = dto.getCertificatePem();
        String thingName = dto.getThingName();
        Optional<Certificate> certificate;

        try {
            certificate = certificateRegistry.getCertificateFromPem(certificatePem);

            if (!certificate.isPresent() || !certificate.get().isActive()) {
                throw new AuthenticationException("Certificate isn't active");
            }

            Thing thing = thingRegistry.getOrCreateThing(thingName);

            VerifyThingAttachedToCertificate verify = useCases.get(VerifyThingAttachedToCertificate.class);
            Result<Boolean> thingAttachedResult = verify.apply(
                    new VerifyThingAttachedToCertificateDTO(thing, certificate.get()));

            if (thingAttachedResult.isOk() && thingAttachedResult.get()) {
                return Result.ok(new SessionImpl(certificate.get(), thing));
            }
        } catch (CloudServiceInteractionException | InvalidCertificateException e) {
            throw new AuthenticationException("Failed to verify certificate with cloud", e);
        }

        throw new AuthenticationException("Failed to verify certificate with attached to thing");
    }

}
