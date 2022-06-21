/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

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
    public static final int REGISTRY_CACHE_SIZE = 50;
    // holds mapping of certificateHash (SHA-256 hash of certificatePem) to IoT Certificate Id;
    // size-bound by default cache size, evicts oldest written entry if the max size is reached
    private static final Map<String, String> certificateHashToIdMap = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(REGISTRY_CACHE_SIZE, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > REGISTRY_CACHE_SIZE;
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
     * @param certificatePem Certificate PEM
     * @return true if the certificate is valid and active.
     */
    public boolean isCertificateValid(String certificatePem) {
        // TODO: Check cache instead of calling the cloud once we have certificate revocation
        Optional<String> certId = fetchActiveCertificateId(certificatePem);
        certId.ifPresent(id -> registerCertificateIdForPem(id, certificatePem));
        return certId.isPresent();
    }

    /**
     * Get IoT Certificate ID for given certificate pem.
     * Active IoT Certificate Ids are cached locally to avoid multiple cloud requests.
     *
     * @param certificatePem Certificate PEM
     * @return IoT Certificate ID or empty Optional if certificate is inactive/invalid
     * @throws IllegalArgumentException for empty certificate PEM
     */
    public Optional<String> getIotCertificateIdForPem(String certificatePem) {
        if (Utils.isEmpty(certificatePem)) {
            throw new IllegalArgumentException("Certificate PEM is empty");
        }
        Optional<String> certId = getAssociatedCertificateId(certificatePem).map(Optional::of)
                .orElseGet(() -> fetchActiveCertificateId(certificatePem));

        certId.ifPresent(id -> registerCertificateIdForPem(id, certificatePem));
        return certId;
    }

    /**
     * Clears registry cache.
     */
    public void clear() {
        certificateHashToIdMap.clear();
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
                .map(certificateHashToIdMap::get);
    }

    /**
     * Locally caches IoT Certificate ID mapping for Certificate PEM.
     *
     * @param certificateId IoT Certificate ID
     * @param certificatePem Certificate PEM
     */
    private void registerCertificateIdForPem(String certificateId, String certificatePem) {
        Optional.ofNullable(getCertificateHash(certificatePem))
                .ifPresent(certHash -> certificateHashToIdMap.put(certHash, certificateId));
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
