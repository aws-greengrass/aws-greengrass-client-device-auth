/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Digest;
import com.aws.greengrass.util.Utils;

import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;


public class CertificateRegistry {
    private static final Logger logger = LogManager.getLogger(CertificateRegistry.class);
    // holds mapping of certificateHash (SHA-256 hash of certificatePem) to IoT Certificate Id;
    // size-bound by default cache size, evicts oldest written entry if the max size is reached
    private final Map<String, String> registry = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(RegistryConfig.REGISTRY_CACHE_SIZE, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > RegistryConfig.REGISTRY_CACHE_SIZE;
                }
            });

    private final IotAuthClient iotAuthClient;

    /**
     * Constructor.
     *
     * @param iotAuthClient IoT Auth Client
     */
    @Inject
    public CertificateRegistry(IotAuthClient iotAuthClient) {
        this.iotAuthClient = iotAuthClient;
    }

    /**
     * Returns whether the provided certificate is valid and active.
     * Returns valid locally registered result when IoT Core cannot be reached.
     *
     * @param certificatePem Certificate PEM
     * @return true if the certificate is valid and active.
     * @throws CloudServiceInteractionException if the certificate cannot be validated
     */
    public boolean isCertificateValid(String certificatePem) {
        try {
            Optional<String> certId = fetchActiveCertificateId(certificatePem);
            updateRegistryForCertificate(certificatePem, certId);
            return certId.isPresent();
        } catch (CloudServiceInteractionException e) {
            return getAssociatedCertificateId(certificatePem)
                    .map(certId -> true)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Get IoT Certificate ID for given certificate pem.
     * Active IoT Certificate Ids are cached locally to avoid multiple cloud requests.
     *
     * @param certificatePem Certificate PEM
     * @return IoT Certificate ID or empty Optional if certificate is inactive/invalid
     * @throws IllegalArgumentException for empty certificate PEM
     * @throws CloudServiceInteractionException if IoT certificate Id cannot be fetched
     */
    public Optional<String> getIotCertificateIdForPem(String certificatePem) {
        if (Utils.isEmpty(certificatePem)) {
            throw new IllegalArgumentException("Certificate PEM is empty");
        }
        try {
            Optional<String> certId = getAssociatedCertificateId(certificatePem).map(Optional::of)
                    .orElseGet(() -> fetchActiveCertificateId(certificatePem));
            updateRegistryForCertificate(certificatePem, certId);
            return certId;
        } catch (CloudServiceInteractionException e) {
            return getAssociatedCertificateId(certificatePem).map(Optional::of)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Retrieves Certificate ID from IoT Core.
     *
     * @param certificatePem Certificate PEM
     * @return IoT Certificate ID or empty Optional if certificate is inactive or invalid
     */
    private Optional<String> fetchActiveCertificateId(String certificatePem) {
        return iotAuthClient.getActiveCertificateId(certificatePem);
    }

    /**
     * Returns IoT Certificate ID associated locally for given certificate PEM.
     *
     * @param certificatePem Certificate PEM
     * @return Certificate ID or empty optional
     */
    private Optional<String> getAssociatedCertificateId(String certificatePem) {
        return Optional.ofNullable(getCertificateHash(certificatePem))
                .map(registry::get);
    }

    /**
     * Locally caches IoT Certificate ID mapping for Certificate PEM.
     *
     * @param certificateId IoT Certificate ID
     * @param certificatePem Certificate PEM
     */
    private void registerCertificateIdForPem(String certificateId, String certificatePem) {
        Optional.ofNullable(getCertificateHash(certificatePem))
                .ifPresent(certHash -> registry.put(certHash, certificateId));
    }

    /**
     * Updates certPem <-> IoT Certificate ID association in the registry.
     *
     * @param certificatePem Certificate PEM
     * @param iotCertificateId Optional of IoT Certificate ID
     */
    private void updateRegistryForCertificate(String certificatePem, Optional<String> iotCertificateId) {
        if (iotCertificateId.isPresent()) {
            registerCertificateIdForPem(iotCertificateId.get(), certificatePem);
        } else {
            clearRegistryForPem(certificatePem);
        }
    }

    /**
     * Removes registry entry for Certificate PEM.
     *
     * @param certificatePem Certificate PEM
     */
    private void clearRegistryForPem(String certificatePem) {
        Optional.ofNullable(getCertificateHash(certificatePem))
                .ifPresent(registry::remove);
    }

    /**
     * Returns SHA-256 hash of given CertificatePem.
     * @param certificatePem certificate pem
     * @return Certificate hash or null if hash could not be calculated
     */
    private String getCertificateHash(String certificatePem) {
        try {
            return Digest.calculate(certificatePem);
        } catch (NoSuchAlgorithmException e) {
            logger.atWarn().cause(e).log("CertificatePem to CertificateId mapping could not be cached");
        }
        return null;
    }
}
