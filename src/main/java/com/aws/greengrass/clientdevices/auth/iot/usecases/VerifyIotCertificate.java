/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.registry.CertificateRegistry;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.util.Optional;
import javax.inject.Inject;

public class VerifyIotCertificate implements UseCases.UseCase<Boolean, String> {
    private static final Logger logger = LogManager.getLogger(VerifyIotCertificate.class);

    private final IotAuthClient iotAuthClient;
    private final CertificateRegistry certificateRegistry;


    /**
     * Verify a certificate with IoT Core.
     *
     * @param iotAuthClient       IoT auth client
     * @param certificateRegistry Certificate Registry
     */
    @Inject
    public VerifyIotCertificate(IotAuthClient iotAuthClient, CertificateRegistry certificateRegistry) {
        this.iotAuthClient = iotAuthClient;
        this.certificateRegistry = certificateRegistry;
    }

    @Override
    public Result<Boolean> apply(String certificatePem) {
        Optional<String> certId = iotAuthClient.getActiveCertificateId(certificatePem);

        // NOTE: This code will not remove certificates from the registry if they are revoked
        //  in IoT Core. This is currently okay, as we will fail those connections during the
        //  TLS handshake.
        // Eventually, we need to handle offline and cert revocation scenarios. However, that
        //  will be handled as part of a subsequent PR, as making that change now breaks a lot
        //  of fragile tests
        if (certId.isPresent()) {
            try {
                Certificate clientCertificate;
                Optional<Certificate> cert = certificateRegistry.getCertificateFromPem(certificatePem);

                if (cert.isPresent()) {
                    clientCertificate = cert.get();
                } else {
                    clientCertificate = certificateRegistry.createCertificate(certificatePem);
                }

                clientCertificate.setStatus(Certificate.Status.ACTIVE);
                certificateRegistry.updateCertificate(clientCertificate);
            } catch (InvalidCertificateException e) {
                // This should never happen
                logger.atError()
                        .kv("CertificateID", certId.get())
                        .log("Unable to create Certificate object from client certificate, despite having received a "
                                + "valid certificate ID from IoT Core");
            }
        }

        return Result.ok(iotAuthClient.getActiveCertificateId(certificatePem).isPresent());
    }
}
