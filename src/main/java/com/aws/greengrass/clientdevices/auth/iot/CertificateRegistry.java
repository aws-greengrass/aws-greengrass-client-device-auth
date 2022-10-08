/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.iot.dto.CertificateV1DTO;
import software.amazon.awssdk.utils.ImmutableMap;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;


public class CertificateRegistry {
    private final RuntimeConfiguration runtimeConfiguration;
    private final Map<Certificate.Status, CertificateV1DTO.Status> domain2dtoStatus = ImmutableMap.of(
            Certificate.Status.ACTIVE, CertificateV1DTO.Status.ACTIVE,
            Certificate.Status.UNKNOWN, CertificateV1DTO.Status.UNKNOWN
    );
    private final Map<CertificateV1DTO.Status, Certificate.Status> dto2domainStatus = ImmutableMap.of(
            CertificateV1DTO.Status.ACTIVE, Certificate.Status.ACTIVE,
            CertificateV1DTO.Status.UNKNOWN, Certificate.Status.UNKNOWN
    );

    @Inject
    public CertificateRegistry(RuntimeConfiguration runtimeConfiguration) {
        this.runtimeConfiguration = runtimeConfiguration;
    }

    /**
     * Retrieve certificate by certificate pem.
     *
     * @param certificatePem cert pem
     * @return certificate object
     * @throws InvalidCertificateException if certificate PEM is invalid
     */
    public Optional<Certificate> getCertificateFromPem(String certificatePem) throws InvalidCertificateException {
        Certificate cert = Certificate.fromPem(certificatePem);
        Optional<CertificateV1DTO> dto = runtimeConfiguration.getCertificateV1(cert.getCertificateId());
        return dto.map(this::certificateV1DTOToCert);
    }

    /**
     * Get a new certificate, creating and storing one if it does not exist.
     * </p>
     * Certificates are created with an initial UNKNOWN state. Callers
     * are responsible for updating the appropriate metadata and then
     * calling {@link #updateCertificate(Certificate)}
     *
     * @param certificatePem Certificate PEM
     * @return certificate object
     * @throws InvalidCertificateException if certificate PEM is invalid
     */
    public Certificate getOrCreateCertificate(String certificatePem) throws InvalidCertificateException {
        Certificate newCert = Certificate.fromPem(certificatePem);
        Optional<CertificateV1DTO> dto = runtimeConfiguration.getCertificateV1(newCert.getCertificateId());
        if (dto.isPresent()) {
            return certificateV1DTOToCert(dto.get());
        }
        runtimeConfiguration.putCertificate(certificateToCertificateV1DTO(newCert));
        return newCert;
    }

    /**
     * Update certificate.
     *
     * @param certificate certificate object
     */
    public void updateCertificate(Certificate certificate) {
        runtimeConfiguration.putCertificate(certificateToCertificateV1DTO(certificate));
    }

    /**
     * Deletes a certificate from the repository.
     *
     * @param certificate certificate to remove
     */
    public void deleteCertificate(Certificate certificate) {
        runtimeConfiguration.removeCertificateV1(certificate.getCertificateId());
    }

    private Certificate certificateV1DTOToCert(CertificateV1DTO dto) {
        Certificate cert = new Certificate(dto.getCertificateId());
        cert.setStatus(dto2domainStatus.get(dto.getStatus()), Instant.ofEpochMilli(dto.getStatusUpdated()));
        return cert;
    }

    private CertificateV1DTO certificateToCertificateV1DTO(Certificate cert) {
        return new CertificateV1DTO(cert.getCertificateId(), domain2dtoStatus.get(cert.getStatus()),
                cert.getStatusLastUpdated().toEpochMilli());
    }
}
