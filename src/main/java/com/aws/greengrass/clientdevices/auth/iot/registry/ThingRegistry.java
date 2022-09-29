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

import java.util.List;
import javax.inject.Inject;

public class ThingRegistry {
    private final IotAuthClient iotAuthClient;
    private final DomainEvents domainEvents;
    private final Repository<String, Thing> repository;

    /**
     * Constructor.
     *
     * @param iotAuthClient IoT Auth Client
     * @param domainEvents  Domain Events
     * @param repository    Repository for storage
     */
    @Inject
    public ThingRegistry(IotAuthClient iotAuthClient,
                         DomainEvents domainEvents,
                         InMemoryRepository<String, Thing> repository) {
        this.iotAuthClient = iotAuthClient;
        this.domainEvents = domainEvents;
        this.repository = repository;
    }

    /**
     * Get or create a Thing.
     * @param thingName ThingName
     * @return Thing object
     */
    public Thing getOrCreateThing(String thingName) {
        Thing thing = getThingInternal(thingName);
        if (thing == null) {
            thing = createThing(thingName);
        }
        return thing;
    }

    /**
     * Create a Thing.
     * @param thingName ThingName
     * @return Thing object
     */
    public Thing createThing(String thingName) {
        Thing newThing = Thing.of(1, thingName);
        storeThing(newThing);
        return newThing.clone();
    }

    /**
     * Retrieve a Thing based on ThingName.
     *
     * @param thingName ThingName
     * @return Thing domain object, if it exists
     */
    public Thing getThing(String thingName) {
        Thing thing = getThingInternal(thingName);
        if (thing != null) {
            return thing.clone();
        }
        return null;
    }

    /**
     * Update a Thing.
     * @param thing Thing which is being updated
     * @return      New Thing version
     */
    public Thing updateThing(Thing thing) {
        // TODO: this method should throw exceptions instead of returning
        //  null if the Thing doesn't exist, if the caller is attempting to
        //  update an old version, or if the Thing hasn't been modified.
        Thing oldThing = getThingInternal(thing.getThingName());

        if (oldThing == null) {
            return null;
        }

        if (thing.getVersion() != oldThing.getVersion()) {
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
        return repository.get(thingName);
    }

    private Thing storeThing(Thing thing) {
        repository.put(thing.getThingName(), thing);
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
