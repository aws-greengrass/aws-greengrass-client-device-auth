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
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;


public class CertificateRegistry implements RefreshableRegistry {
    private static final Logger logger = LogManager.getLogger(CertificateRegistry.class);
    // holds mapping of certificateHash (SHA-256 hash of certificatePem) to its registry entry;
    // size-bound by default cache size, evicts oldest written entry if the max size is reached
    static final Map<String, CertificateRegistryEntry> registry = Collections.synchronizedMap(
            new LinkedHashMap<String, CertificateRegistryEntry>(RegistryConfig.REGISTRY_CACHE_SIZE, 0.75f, false) {
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
     * @param certificatePem Certificate PEM
     * @return true if the certificate is valid and active.
     */
    public boolean isCertificateValid(String certificatePem) {
        try {
            Optional<String> certId = fetchActiveCertificateId(certificatePem);
            if (certId.isPresent()) {
                registerCertificateIdForPem(certId.get(), certificatePem);
            } else {
                removeAssociatedRegistryEntry(certificatePem);
            }
            return certId.isPresent();
        } catch (CloudServiceInteractionException e) {
            return getAssociatedCertificateId(certificatePem).isPresent();
        }
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
        Optional<String> certId;
        try {
            certId = getAssociatedCertificateId(certificatePem).map(Optional::of)
                    .orElseGet(() -> fetchActiveCertificateId(certificatePem));

            certId.ifPresent(id -> registerCertificateIdForPem(id, certificatePem));
        } catch (CloudServiceInteractionException e) {
            certId = Optional.empty();
        }
        return certId;
    }

    /**
     * Removes stale (invalid) entries from the registry.
     * TODO: also sync with cloud
     */
    @Override
    public void refresh() {
        registry.values().removeIf(registryEntry -> !registryEntry.isValid());
    }

    /**
     * Clears registry cache.
     */
    public void clear() {
        registry.clear();
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
     * Ignores invalid (stale) registry entries.
     *
     * @param certificatePem Certificate PEM
     * @return Certificate ID or empty optional
     */
    private Optional<String> getAssociatedCertificateId(String certificatePem) {
        return Optional.ofNullable(getCertificateHash(certificatePem))
                .map(registry::get)
                .filter(CertificateRegistryEntry::isValid)
                .map(CertificateRegistryEntry::getIotCertificateId);
    }

    /**
     * Removes registry entry associated with given CertificatePem.
     *
     * @param certificatePem Certificate PEM
     */
    private void removeAssociatedRegistryEntry(String certificatePem) {
        Optional.ofNullable(getCertificateHash(certificatePem))
                .map(registry::remove);
    }

    /**
     * Locally caches IoT Certificate ID mapping for Certificate PEM.
     *
     * @param certificateId IoT Certificate ID
     * @param certificatePem Certificate PEM
     */
    private void registerCertificateIdForPem(String certificateId, String certificatePem) {
        Optional.ofNullable(getCertificateHash(certificatePem))
                .ifPresent(certHash ->
                        registry.put(certHash, new CertificateRegistryEntry(
                                Instant.now().plusSeconds(RegistryConfig.REGISTRY_ENTRY_TTL_SECONDS),
                                certHash, certificateId)));
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
