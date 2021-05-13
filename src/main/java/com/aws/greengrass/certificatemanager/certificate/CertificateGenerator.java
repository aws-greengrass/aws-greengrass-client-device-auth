package com.aws.greengrass.certificatemanager.certificate;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public abstract class CertificateGenerator {
    static final long DEFAULT_CERT_EXPIRY_SECONDS = 60 * 60 * 24 * 7; // 1 week

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

    public abstract void generateCertificate() throws KeyStoreException,
            OperatorCreationException, CertificateException, NoSuchAlgorithmException, IOException;

    /**
     * Checks if certificate needs to be regenerated.
     *
     * @return true if certificate should be regenerated, else false
     */
    public boolean shouldRegenerate() {
        if (certificate == null) {
            return true;
        }

        Instant dayFromNow = Instant.now(clock).plus(1, ChronoUnit.DAYS);
        Instant expiryTime = certificate.getNotAfter().toInstant();
        return expiryTime.isBefore(dayFromNow);
    }
}
