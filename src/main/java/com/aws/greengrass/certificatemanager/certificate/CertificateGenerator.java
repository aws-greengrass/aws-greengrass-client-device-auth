/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.asn1.x500.X500Name;

import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

public abstract class CertificateGenerator {
    protected final X500Name subject;
    protected final PublicKey publicKey;
    protected final CertificateStore certificateStore;
    protected final CertificatesConfig certificatesConfig;

    @Getter(AccessLevel.PACKAGE)
    protected X509Certificate certificate;
    @Setter(AccessLevel.PACKAGE) // for unit tests
    protected Clock clock;

    /**
     * Construct a new CertificateGenerator.
     *
     * @param subject            X500 subject
     * @param publicKey          Public Key
     * @param certificateStore   CertificateStore instance
     * @param certificatesConfig Certificate configuration
     * @param clock              clock
     */
    public CertificateGenerator(X500Name subject,
                                PublicKey publicKey,
                                CertificateStore certificateStore,
                                CertificatesConfig certificatesConfig,
                                Clock clock) {
        this.subject = subject;
        this.publicKey = publicKey;
        this.certificateStore = certificateStore;
        this.certificatesConfig = certificatesConfig;
        this.clock = clock;
    }

    /**
     * Generate a new certificate. This is a potentially expensive operation, especially
     * for low-powered devices, so consider calling this asynchronously.
     *
     * @param connectivityInfoSupplier connectivity information
     * @param reason                   WHY cert gen was requested.
     * @throws KeyStoreException if unable to retrieve CA key/cert
     */
    public abstract void generateCertificate(Supplier<List<String>> connectivityInfoSupplier, String reason)
            throws KeyStoreException;

    /**
     * Get expiry time of certificate.
     *
     * @return expiry time
     */
    protected Instant getExpiryTime() {
        if (certificate == null) {
            return Instant.MIN;
        }
        return certificate.getNotAfter().toInstant();
    }
}
