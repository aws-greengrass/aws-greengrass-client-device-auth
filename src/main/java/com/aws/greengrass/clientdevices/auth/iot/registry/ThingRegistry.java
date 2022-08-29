/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

public class ThingRegistry {
    // holds mapping of thingName to its associated certificates;
    // size-bound by default cache size, evicts oldest written entry if the max size is reached
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, Set<CertificateEntry>> registry = Collections.synchronizedMap(
            new LinkedHashMap<String, Set<CertificateEntry>>(RegistryConfig.REGISTRY_CACHE_SIZE, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > RegistryConfig.REGISTRY_CACHE_SIZE;
                }
            });

    private final IotAuthClient iotAuthClient;

    @Inject
    public ThingRegistry(IotAuthClient iotAuthClient, RegistryRefreshScheduler refreshScheduler) {
        this.iotAuthClient = iotAuthClient;
        refreshScheduler.schedule(this::refreshRegistry);
    }

    /**
     * Returns whether the Thing is associated to the given IoT Certificate.
     * Returns valid locally registered result when IoT Core cannot be reached.
     * TODO: add a separate refreshable caching layer for offline auth
     *
     * @param thing IoT Thing
     * @param certificate IoT Certificate
     * @return whether thing is attached to the certificate
     * @throws CloudServiceInteractionException when thing <-> certificate association cannot be verified
     */
    public boolean isThingAttachedToCertificate(Thing thing, Certificate certificate) {
        try {
            if (iotAuthClient.isThingAttachedToCertificate(thing, certificate)) {
                registerCertificateForThing(thing, certificate);
                return true;
            } else {
                clearThingCertificateAssociation(thing, certificate);
            }
        } catch (CloudServiceInteractionException e) {
            if (isCertificateRegisteredForThing(thing, certificate)) {
                return true;
            }
            throw e;
        }
        return false;
    }

    private void registerCertificateForThing(Thing thing, Certificate certificate) {
        Set<CertificateEntry> certEntrySet = registry.computeIfAbsent(thing.getThingName(), s -> new HashSet<>());
        // replace previous association with a new entry with extended TTL
        clearThingCertificateAssociation(thing, certificate);
        certEntrySet.add(getNewCertificateEntry(certificate));
        registry.put(thing.getThingName(), certEntrySet);
    }

    private void clearThingCertificateAssociation(Thing thing, Certificate certificate) {
        Set<CertificateEntry> certSet = registry.get(thing.getThingName());
        if (certSet != null) {
            certSet.removeIf(certificateEntry ->
                    certificateEntry.getIotCertificateId().equals(certificate.getIotCertificateId()));
        }
    }

    private boolean isCertificateRegisteredForThing(Thing thing, Certificate certificate) {
        Set<CertificateEntry> certSet = registry.get(thing.getThingName());
        if (certSet != null) {
            return certSet.stream()
                    .anyMatch(certEntry -> certEntry.getIotCertificateId().equals(certificate.getIotCertificateId()));
        }
        return false;
    }

    private CertificateEntry getNewCertificateEntry(Certificate certificate) {
        return new CertificateEntry(Instant.now().plusSeconds(RegistryConfig.REGISTRY_CACHE_ENTRY_TTL_SECONDS),
                null, certificate.getIotCertificateId());
    }

    private boolean isValidCertificateEntry(CertificateEntry certEntry) {
        return certEntry.getValidTill().isAfter(Instant.now());
    }

    /**
     * Removes stale (invalid) registry entries.
     */
    void refreshRegistry() {
        registry.values().forEach(certificateEntries ->
                certificateEntries.removeIf(certEntry -> !isValidCertificateEntry(certEntry)));
        registry.values().removeIf(Set::isEmpty);
    }
}
