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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

public class ThingRegistry {
    // holds mapping of thingName to IoT Certificate IDs;
    // size-bound by default cache size, evicts oldest written entry if the max size is reached
    private final Map<String, Thing> registry = Collections.synchronizedMap(
            new LinkedHashMap<String, Thing>(RegistryConfig.REGISTRY_CACHE_SIZE, 0.75f, false) {
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
     * Create a Thing.
     * @param thingName ThingName
     * @return Thing object
     */
    public Thing createThing(String thingName) {
        return updateThing(Thing.of(thingName));
    }

    /**
     * Retrieve a Thing based on ThingName.
     *
     * @param thingName ThingName
     * @return Thing domain object, if it exists
     */
    public Thing getThing(String thingName) {
        return Thing.of(getThingInternal(thingName));
    }

    /**
     * Update a Thing.
     * @param thing Thing which is being updated
     * @return      New Thing version
     */
    public Thing updateThing(Thing thing) {
        // TODO - need to throw exception if provided Thing is not the most recent version
        Thing oldThing = getThingInternal(thing.getThingName());

        if (oldThing == null) {
            storeThing(thing);
            return thing;
        }

        if (thing.getVersion() != oldThing.getVersion()) {
            // TODO: throw exception since caller is trying to update an old version
            return null;
        }

        if (!thing.isModified()) {
            // Nothing to modify - return old object
            return thing;
        }

        Thing newThing = Thing.of(thing.getVersion() + 1, thing.getThingName(), thing.getAttachedCertificateIds());
        return storeThing(newThing);
    }

    private Thing getThingInternal(String thingName) {
        return registry.get(thingName);
    }

    private Thing storeThing(Thing thing) {
        registry.put(thing.getThingName(), thing);
        domainEvents.emit(new ThingUpdated(thing.getThingName(), thing.getVersion()));
        return thing;
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
                thing.attachCertificate(certificate.getIotCertificateId());
                updateThing(thing);
                return true;
            } else {
                thing.detachCertificate(certificate.getIotCertificateId());
                updateThing(thing);
            }
        } catch (CloudServiceInteractionException e) {
            List<String> certIds = thing.getAttachedCertificateIds();
            if (certIds.contains(certificate.getIotCertificateId())) {
                return true;
            }
            throw e;
        }
        return false;
    }
}
