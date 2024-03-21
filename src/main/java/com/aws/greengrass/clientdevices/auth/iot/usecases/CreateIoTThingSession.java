/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
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
import com.aws.greengrass.util.Pair;

import javax.inject.Inject;

import static com.aws.greengrass.clientdevices.auth.iot.Thing.MAX_THING_NAME_LENGTH;

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
        if (dto.getThingName() != null && dto.getThingName().length() > MAX_THING_NAME_LENGTH) {
            throw new AuthenticationException("Thing name is too long");
        }

        Certificate certificate = getActiveCertificateFromRegistry(dto);
        String thingName = dto.getThingName();
        Pair<Thing, Boolean> thing = thingRegistry.getOrCreateThing(thingName);

        VerifyThingAttachedToCertificate.Result result =
                useCases.get(VerifyThingAttachedToCertificate.class)
                        .apply(new VerifyThingAttachedToCertificateDTO(thingName, certificate.getCertificateId()));

        logger.atDebug()
                .kv("thingName", thingName)
                .kv("thingHasValidAttachment", result.isThingHasValidAttachmentToCertificate())
                .kv("lastAttachedOn", result.getLastAttached())
                .kv("attachmentExpiration", result.getAttachmentExpiration())
                .kv("source", result.getVerificationSource())
                .log("Attachment verification result");

        if (result.isThingHasValidAttachmentToCertificate()) {
            return new SessionImpl(certificate, thing.getLeft());
        }

        // If the thing was newly created just for this request, then remove it from the config if validation failed.
        if (thing.getRight()) {
            thingRegistry.deleteThing(thing.getLeft());
        }
        throw new AuthenticationException("Failed to verify certificate attached to thing");
    }

    private Certificate getActiveCertificateFromRegistry(CreateSessionDTO dto) throws AuthenticationException {
        Certificate certificate;
        try {
            certificate = certificateRegistry.getCertificateFromPem(dto.getCertificatePem())
                    .orElseThrow(() -> new AuthenticationException("Certificate not in local registry"));
        } catch (InvalidCertificateException e) {
            throw new AuthenticationException("Certificate is invalid", e);
        }
        if (certificate.isActive()) {
            return certificate;
        }
        if (certificate.isStatusTrusted()) {
            throw new AuthenticationException("Certificate is not active");
        } else {
            throw new AuthenticationException("Certificate is not trusted");
        }
    }
}
