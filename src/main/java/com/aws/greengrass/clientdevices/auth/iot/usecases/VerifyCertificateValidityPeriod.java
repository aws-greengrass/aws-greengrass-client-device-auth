/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Date;
import javax.inject.Inject;

public class VerifyCertificateValidityPeriod implements UseCases.UseCase<Boolean, String> {
    private static final Logger logger = LogManager.getLogger(VerifyCertificateValidityPeriod.class);
    private final Clock clock;

    /**
     * Verify that a certificate is valid.
     * </p>
     * This method does not determine whether a certificate is trusted. It simply checks that the current system time
     * is within the validity period specified within the certificate.
     *
     * @param clock System clock.
     */
    @Inject
    public VerifyCertificateValidityPeriod(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Boolean apply(String certificatePem) {
        CertificateFactory cf = null;

        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            logger.atError().cause(e).log("Unable to create X.509 certificate factory");
            return false;
        }

        try (InputStream is = new ByteArrayInputStream(certificatePem.getBytes(StandardCharsets.UTF_8))) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
            Date now = Date.from(clock.instant());
            try {
                cert.checkValidity(now);
                return true;
            } catch (CertificateExpiredException e) {
                logger.atWarn().kv("notAfter", cert.getNotAfter()).log("Rejecting expired certificate");
            } catch (CertificateNotYetValidException e) {
                logger.atWarn().kv("notBefore", cert.getNotBefore()).log("Rejecting not yet valid certificate");
            }
        } catch (IOException | CertificateException e) {
            logger.atWarn().cause(e).log("Unable to parse client certificate");
        }

        return false;
    }
}
