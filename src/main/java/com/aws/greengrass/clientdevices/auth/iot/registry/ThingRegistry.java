/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.events.ThingUpdated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

public class ThingRegistry {
    // holds mapping of thingName to IoT Certificate IDs;
    // size-bound by default cache size, evicts oldest written entry if the max size is reached
    private final Map<String, List<String>> registry = Collections.synchronizedMap(
            new LinkedHashMap<String, List<String>>(RegistryConfig.REGISTRY_CACHE_SIZE, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > RegistryConfig.REGISTRY_CACHE_SIZE;
                }
            });

    private final IotAuthClient iotAuthClient;
    private final DomainEvents domainEvents;

    @Inject
    public ThingRegistry(IotAuthClient iotAuthClient, DomainEvents domainEvents) {
        this.iotAuthClient = iotAuthClient;
        this.domainEvents = domainEvents;
    }

    /**
     * Retrieve a Thing based on ThingName.
     *
     * @param thingName ThingName
     * @return Thing domain object
     */
    public Thing getThing(String thingName) {
        List<String> certificateIds = registry.get(thingName);
        if (certificateIds != null) {
            return new Thing(thingName, certificateIds);
        }
        return null;
    }

    /**
     * Attach a certificate to Thing.
     * </p>
     * If the provided Thing does not exist, it will be created.
     * @param thingName     ThingName.
     * @param certificateId Certificate ID to attach.
     */
    public synchronized void attachCertificateToThing(String thingName, String certificateId) {
        List<String> certificateIds = registry.get(thingName);

        if (certificateIds != null && certificateIds.contains(certificateId)) {
            // Nothing to do
            return;
        }

        // Thing doesn't exist - create it
        if (certificateIds == null) {
            certificateIds = new ArrayList<>(Collections.singletonList(certificateId));
            registry.put(thingName, certificateIds);
        } else {
            certificateIds.add(certificateId);
        }

        domainEvents.emit(
                new ThingUpdated(thingName, new ArrayList<>(certificateIds)));
    }

    /**
     * Detach a certificate from Thing.
     * @param thingName     ThingName.
     * @param certificateId Certificate ID to detach.
     */
    public synchronized void detachCertificateFromThing(String thingName, String certificateId) {
        List<String> certificateIds = registry.get(thingName);

        if (certificateIds == null || !certificateIds.contains(certificateId)) {
            // Nothing to do
            return;
        }

        certificateIds.remove(certificateId);
        domainEvents.emit(
                new ThingUpdated(thingName, new ArrayList<>(certificateIds)));
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
                clearRegistryForThing(thing);
            }
        } catch (CloudServiceInteractionException e) {
            if (isCertificateRegisteredForThing(thing.getThingName(), certificate.getIotCertificateId())) {
                return true;
            }
            throw e;
        }
        return false;
    }

    private void registerCertificateForThing(Thing thing, Certificate certificate) {
        registry.put(thing.getThingName(),
                new ArrayList<>(Collections.singletonList(certificate.getIotCertificateId())));
    }

    private void clearRegistryForThing(Thing thing) {
        registry.remove(thing.getThingName());
    }

    private boolean isCertificateRegisteredForThing(String thingName, String certificateId) {
        Thing newThing = getThing(thingName);
        return newThing != null && newThing.getAttachedCertificateIds().contains(certificateId);
    }
}
