/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.Result;
import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.iot.registry.CertificateRegistry;

import javax.inject.Inject;

public class VerifyIotCertificate implements UseCases.UseCase<Boolean, String> {
    private final CertificateRegistry certificateRegistry;


    /**
     * Register core certificate authority with Greengrass cloud.
     *
     * @param certificateRegistry Certificate Registry
     */
    @Inject
    public VerifyIotCertificate(CertificateRegistry certificateRegistry) {
        this.certificateRegistry = certificateRegistry;
    }

    @Override
    public Result<Boolean> apply(String certificatePem) {
        return Result.ok(certificateRegistry.isCertificateValid(certificatePem));

        /*
        String source = "cloud";
        Certificate iotCert;

        try {
            iotCert = iotAuthClient.getIotCertificate(certificatePem);
        } catch (InvalidCertificateException e) {
            return Result.ok(false);
        }

        logger.atDebug().kv("CertificateID", iotCert.getCertificateId())
                .kv("status", iotCert.getStatus())
                .kv("source", source)
                .log("Verifying client device certificate");

        return Result.ok(iotCert.isActive());
         */
    }
}
