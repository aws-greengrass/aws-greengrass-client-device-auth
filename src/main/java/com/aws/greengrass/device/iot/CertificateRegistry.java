/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

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

import static com.aws.greengrass.device.ClientDevicesAuthService.DEFAULT_MAX_ACTIVE_AUTH_TOKENS;


public class CertificateRegistry {
    private static final Logger logger = LogManager.getLogger(CertificateRegistry.class);
    public static final int REGISTRY_CACHE_SIZE = DEFAULT_MAX_ACTIVE_AUTH_TOKENS;
    // holds mapping of certificateHash (SHA-256 hash of certificatePem) to IoT Certificate Id;
    // size-bound by default cache size, evicts oldest written entry if the max size is reached
    private static final Map<String, String> certificateHashToIdMap = Collections.synchronizedMap(
            new LinkedHashMap<>(REGISTRY_CACHE_SIZE, 0.75f, false));

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

        String certId = getAssociatedCertificateId(certificatePem)
                .orElseGet(() -> fetchActiveCertificateId(certificatePem).orElse(null));

        if (certId != null) {
            registerCertificateIdForPem(certId, certificatePem);
            return Optional.of(certId);
        }
        return Optional.empty();
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

    private Optional<String> getAssociatedCertificateId(String certificatePem) {
        try {
            String certId = certificateHashToIdMap.get(Digest.calculate(certificatePem));
            if (certId != null) {
                return Optional.of(certId);
            }
        } catch (NoSuchAlgorithmException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Locally caches IoT Certificate ID mapping for Certificate PEM.
     *
     * @param certificateId IoT Certificate ID
     * @param certificatePem Certificate PEM
     */
    private void registerCertificateIdForPem(String certificateId, String certificatePem) {
        try {
            certificateHashToIdMap.put(Digest.calculate(certificatePem), certificateId);
        } catch (NoSuchAlgorithmException e) {
            logger.atWarn().cause(e).log("Could not store CertificatePem to CertificateId mapping");
        }
    }
}
