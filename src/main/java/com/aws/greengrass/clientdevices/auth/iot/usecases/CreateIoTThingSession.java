/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

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
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.SessionImpl;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.Optional;
import javax.inject.Inject;

public class CreateIoTThingSession implements UseCases.UseCase<Session, CreateSessionDTO> {
    private static final Logger logger = LogManager.getLogger(CreateIoTThingSession.class);
    private final ThingRegistry thingRegistry;
    private final CertificateRegistry certificateRegistry;
    private final UseCases useCases;


    /**
     * Verify a certificate with IoT Core.
     *
     * @param thingRegistry       Thing Registry
     * @param certificateRegistry Certificate Registry
     * @param useCases            UseCases service
     */
    @Inject
    public CreateIoTThingSession(ThingRegistry thingRegistry, CertificateRegistry certificateRegistry,
                                 UseCases useCases) {
        this.thingRegistry = thingRegistry;
        this.certificateRegistry = certificateRegistry;
        this.useCases = useCases;
    }


    /**
     * Creates an IoT session if the thing is attached to an active certificate.
     *
     * @param dto - VerifyCertificateAttachedToThingDTO
     */
    @Override
    public Session apply(CreateSessionDTO dto) throws AuthenticationException {
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
            VerifyThingAttachedToCertificate.Result result = verify.apply(
                    new VerifyThingAttachedToCertificateDTO(thingName, certificate.get().getCertificateId()));

            logger.atDebug()
                    .kv("thingHasValidAttachment", result.isThingHasValidAttachmentToCertificate())
                    .kv("lastAttachedOn", result.getLastAttached())
                    .kv("attachmentExpiration", result.getAttachmentExpiration())
                    .kv("source", result.getVerificationSource())
                    .log("Attachment verification result");

            if (result.isThingHasValidAttachmentToCertificate()) {
                return new SessionImpl(certificate.get(), thing);
            }
        } catch (CloudServiceInteractionException | InvalidCertificateException e) {
            throw new AuthenticationException("Failed to verify certificate with cloud", e);
        }

        throw new AuthenticationException("Failed to verify certificate attached to thing");
    }
}
