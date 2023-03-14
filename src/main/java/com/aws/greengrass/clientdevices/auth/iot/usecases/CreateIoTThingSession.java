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

import javax.inject.Inject;

public class CreateIoTThingSession implements UseCases.UseCase<Session, CreateSessionDTO> {
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
    @SuppressWarnings("PMD.PrematureDeclaration")
    public Session apply(CreateSessionDTO dto) throws AuthenticationException {
        String certificatePem = dto.getCertificatePem();
        String thingName = dto.getThingName();

        Certificate certificate;
        try {
            certificate = certificateRegistry.getCertificateFromPem(certificatePem)
                    .filter(Certificate::isActive)
                    .orElseThrow(() -> new AuthenticationException("Certificate isn't active"));
        } catch (InvalidCertificateException e) {
            throw new AuthenticationException("Failed to verify certificate", e);
        }

        Thing thing = thingRegistry.getOrCreateThing(thingName);
        try {
            boolean thingAttachedToCertificate = useCases.get(VerifyThingAttachedToCertificate.class)
                    .apply(new VerifyThingAttachedToCertificateDTO(thingName, certificate.getCertificateId()));
            if (!thingAttachedToCertificate) {
                throw new AuthenticationException("Thing not attached to certificate");
            }
        } catch (CloudServiceInteractionException e) {
            throw new AuthenticationException("Failed to verify certificate with cloud", e);
        } catch (LocalVerificationException e) {
            throw new AuthenticationException("Failed to verify thing attached certificate", e);
        }

        return new SessionImpl(certificate, thing);
    }
}
