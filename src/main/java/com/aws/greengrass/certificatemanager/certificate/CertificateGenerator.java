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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

public abstract class CertificateGenerator {
    static final long DEFAULT_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 7; // 1 week

    @Getter(AccessLevel.PACKAGE)
    protected final X500Name subject;
    protected final PublicKey publicKey;
    protected final CertificateStore certificateStore;

    @Getter(AccessLevel.PACKAGE)
    protected X509Certificate certificate;
    @Setter(AccessLevel.PACKAGE)
    protected Clock clock = Clock.systemUTC();

    /**
     * Constructor.
     *
     * @param subject          X500 subject
     * @param publicKey        Public Key
     * @param certificateStore CertificateStore instance
     */
    public CertificateGenerator(X500Name subject, PublicKey publicKey, CertificateStore certificateStore) {
        this.subject = subject;
        this.publicKey = publicKey;
        this.certificateStore = certificateStore;
    }

    public abstract void generateCertificate(Supplier<List<String>> connectivityInfoSupplier)
            throws KeyStoreException;

    /**
     * Checks if certificate needs to be regenerated.
     *
     * @return true if certificate should be regenerated, else false
     */
    public boolean shouldRegenerate() {
        Instant dayFromNow = Instant.now(clock).plus(1, ChronoUnit.DAYS);
        Instant expiryTime = getExpiryTime();
        return expiryTime.isBefore(dayFromNow);
    }

    /**
     * Get expiry time of certificate.
     *
     * @return expiry time
     */
    public Instant getExpiryTime() {
        if (certificate == null) {
            return Instant.MIN;
        }

        return certificate.getNotAfter().toInstant();
    }
}
