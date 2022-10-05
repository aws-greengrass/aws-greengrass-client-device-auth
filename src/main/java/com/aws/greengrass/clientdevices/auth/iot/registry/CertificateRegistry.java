/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;


public class CertificateRegistry {
    // holds certificate metadata -- this is a temporary implementation
    // which will be replaced in subsequent PRs
    private final Map<String, Certificate> registry = Collections.synchronizedMap(
            new LinkedHashMap<String, Certificate>(RegistryConfig.REGISTRY_CACHE_SIZE, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > RegistryConfig.REGISTRY_CACHE_SIZE;
                }
            });

    /**
     * Retrieve certificate by certificate pem.
     *
     * @param certificatePem cert pem
     * @return certificate object
     * @throws InvalidCertificateException if certificate PEM is invalid
     */
    public Optional<Certificate> getCertificateFromPem(String certificatePem) throws InvalidCertificateException {
        Certificate cert = Certificate.fromPem(certificatePem);
        return getCertificateById(cert.getCertificateId());
    }

    /**
     * Create and store a new certificate.
     * </p>
     * Certificates are created with an initial UNKNOWN state. Callers
     * are responsible for updating the appropriate metadata and then
     * calling {@link #updateCertificate(Certificate)}
     *
     * @param certificatePem Certificate PEM
     * @return certificate object
     * @throws InvalidCertificateException if certificate PEM is invalid
     */
    public Certificate createCertificate(String certificatePem) throws InvalidCertificateException {
        Certificate newCert = Certificate.fromPem(certificatePem);
        updateCertificate(newCert);
        return newCert;
    }

    /**
     * Update certificate.
     *
     * @param certificate certificate object
     */
    public void updateCertificate(Certificate certificate) {
        storeCertificateMetadata(certificate);
    }

    /**
     * Removes a certificate from the registry.
     *
     * @param certificate certificate to remove
     */
    public void removeCertificate(Certificate certificate) {
        removeCertificateMetadata(certificate);
    }

    private void storeCertificateMetadata(Certificate certificate) {
        registry.put(certificate.getCertificateId(), certificate);
    }

    private void removeCertificateMetadata(Certificate certificate) {
        registry.remove(certificate.getCertificateId());
    }

    private Optional<Certificate> getCertificateById(String certificateId) {
        Certificate cert = registry.get(certificateId);
        return Optional.ofNullable(cert);
    }
}
