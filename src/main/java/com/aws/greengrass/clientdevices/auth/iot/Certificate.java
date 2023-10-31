/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.StringLiteralAttribute;
import lombok.AccessLevel;
import lombok.Getter;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES;

@Getter
public class Certificate implements AttributeProvider {
    public static final String NAMESPACE = "Certificate";
    private static final AtomicInteger metadataTrustDurationMinutes =
            new AtomicInteger(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES);

    public enum Status {
        ACTIVE, UNKNOWN
    }

    private String certificateId;
    @Getter(AccessLevel.NONE)
    private Status status;
    private Instant statusLastUpdated;


    /**
     * Builds a new Certificate instance given its id generated from PEM file.
     *
     * @param certificateId - a certificateId
     */
    Certificate(String certificateId) {
        this.certificateId = certificateId;
        this.status = Status.UNKNOWN;
        this.statusLastUpdated = Instant.EPOCH; // Treat epoch as beginning of time
    }

    /**
     * Factory method to construct a Certificate object from certificate PEM.
     *
     * @param certificatePem Certificate PEM
     * @return Certificate
     * @throws InvalidCertificateException if certificate PEM is invalid
     */
    public static Certificate fromPem(String certificatePem) throws InvalidCertificateException {
        try {
            String certificateId = computeCertificateId(certificatePem);
            return new Certificate(certificateId);
        } catch (CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new InvalidCertificateException("Unable to parse certificate PEM", e);
        }
    }

    /**
     * Set certificate status as of the current time.
     *
     * @param status Certificate status
     */
    public void setStatus(Status status) {
        setStatus(status, Instant.now());
    }

    /**
     * Set certificate status as of the provided time.
     *
     * @param status      Certificate status
     * @param lastUpdated Timestamp
     */
    public void setStatus(Status status, Instant lastUpdated) {
        this.status = status;
        this.statusLastUpdated = lastUpdated;
    }

    /**
     * Check certificate status.
     *
     * @return true if this certificate is verified and active in IoT Core.
     */
    public boolean isActive() {
        return getStatus() == Status.ACTIVE;
    }

    /**
     * Retrieve certificate PEM.
     *
     * @return certificate PEM as a UTF-8 encoded string
     * @throws UnsupportedOperationException since this is not yet supported
     */
    public String getCertificatePem() {
        throw new UnsupportedOperationException("Retrieving certificate PEM currently not supported");
    }

    /**
     * Determined whether this certificate was updated after another.
     *
     * @param cert - another Certificate
     */
    public boolean wasUpdatedAfter(Certificate cert) {
        if (statusLastUpdated == null) {
            return false;
        }

        return statusLastUpdated.isAfter(cert.getStatusLastUpdated());
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public Map<String, DeviceAttribute> getDeviceAttributes() {
        return Collections.singletonMap("CertificateId", new StringLiteralAttribute(getCertificateId()));
    }

    /**
     * Returns the trusted status.
     *
     * @return certificate status
     */
    public Status getStatus() {
        if (isStatusTrusted()) {
            return status;
        }
        return Status.UNKNOWN;
    }

    /**
     * Updates the duration for which a certificate metadata can be trusted.
     *
     * @param newTrustDuration desired trust duration in minutes
     */
    public static void updateMetadataTrustDurationMinutes(int newTrustDuration) {
        metadataTrustDurationMinutes.set(newTrustDuration);
    }

    public boolean isStatusTrusted() {
        Instant validTill = statusLastUpdated.plus(metadataTrustDurationMinutes.get(), ChronoUnit.MINUTES);
        return validTill.isAfter(Instant.now());
    }

    private static String computeCertificateId(String certificatePem)
            throws CertificateException, NoSuchAlgorithmException, IOException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream is = new ByteArrayInputStream(certificatePem.getBytes(StandardCharsets.UTF_8))) {
            X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            return new String(Hex.encode(digest.digest(cert.getEncoded())), StandardCharsets.UTF_8);
        }
    }
}
