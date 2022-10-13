/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import lombok.Getter;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CertificateFake extends Certificate {
    @Getter
    private final String certificatePem;

    private CertificateFake(String certificatePem, String certificateId) {
        super(certificateId);
        this.certificatePem = certificatePem;
    }

    public static CertificateFake of(String certPem) throws InvalidCertificateException {
        return new CertificateFake(certPem, CertificateFake.computeCertificateId(certPem));
    }

    public static CertificateFake activeCertificate() throws InvalidCertificateException {
        return CertificateFake.activeCertificate("");
    }

    public static CertificateFake activeCertificate(String certPem) throws InvalidCertificateException {
        CertificateFake cert = CertificateFake.of(certPem);
        cert.setStatus(Status.ACTIVE);
        return cert;
    }

    private static String computeCertificateId(String certPem) throws InvalidCertificateException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            return new String(Hex.encode(digest.digest(certPem.getBytes(StandardCharsets.UTF_8))), StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            throw new InvalidCertificateException("Unable to compute certificate ID", e);
        }
    }
}
