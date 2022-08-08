/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

public class ThingRegistry implements RefreshableRegistry {
    // holds mapping of thingName to its registry entry;
    // size-bound by default cache size, evicts oldest written entry if the max size is reached
    static final Map<String, ThingRegistryEntry> registry = Collections.synchronizedMap(
            new LinkedHashMap<String, ThingRegistryEntry>(RegistryConfig.REGISTRY_CACHE_SIZE, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > RegistryConfig.REGISTRY_CACHE_SIZE;
                }
            });

    private final IotAuthClient iotAuthClient;

    @Inject
    public ThingRegistry(IotAuthClient iotAuthClient) {
        this.iotAuthClient = iotAuthClient;
    }

    /**
     * Returns whether the Thing is associated to the given IoT Certificate.
     * Returns locally registered result when IoT Core cannot be reached.
     *
     * @param thing IoT Thing
     * @param certificate IoT Certificate
     * @return whether thing is attached to the certificate
     */
    public boolean isThingAttachedToCertificate(Thing thing, Certificate certificate) {
        try {
            if (iotAuthClient.isThingAttachedToCertificate(thing, certificate)) {
                registerCertificateForThing(thing, certificate);
                return true;
            } else {
                clearRegistryForThing(thing);
            }
        } catch (CloudServiceInteractionException e) {
            return isCertificateRegisteredForThing(thing, certificate);
        }
        return false;
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

    private void registerCertificateForThing(Thing thing, Certificate certificate) {
        registry.put(thing.getThingName(),
                new ThingRegistryEntry(Instant.now().plusSeconds(RegistryConfig.REGISTRY_ENTRY_TTL_SECONDS),
                        thing.getThingName(), certificate.getIotCertificateId()));
    }

    private void clearRegistryForThing(Thing thing) {
        registry.remove(thing.getThingName());
    }

    private boolean isCertificateRegisteredForThing(Thing thing, Certificate certificate) {
        return Optional.ofNullable(registry.get(thing.getThingName()))
                .filter(RefreshableRegistryEntry::isValid)
                .filter(thingRegistryEntry ->
                        thingRegistryEntry.getIotCertificateId().equals(certificate.getIotCertificateId()))
                .isPresent();
    }
}
