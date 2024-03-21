/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.infra;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.ThingV1DTO;
import com.aws.greengrass.clientdevices.auth.iot.events.ThingUpdated;
import com.aws.greengrass.util.Pair;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

public class ThingRegistry {
    private final DomainEvents domainEvents;
    private final RuntimeConfiguration runtimeConfig;

    /**
     * Construct Thing registry.
     *
     * @param domainEvents  Domain events
     * @param runtimeConfig Runtime configuration store
     */
    @Inject
    public ThingRegistry(DomainEvents domainEvents, RuntimeConfiguration runtimeConfig) {
        this.domainEvents = domainEvents;
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * Get or create a Thing.
     *
     * @param thingName ThingName
     * @return Thing object and if the thing was newly created
     */
    public Pair<Thing, Boolean> getOrCreateThing(String thingName) {
        Thing thing = getThingInternal(thingName);
        if (thing == null) {
            thing = createThing(thingName);
            return new Pair<>(thing, true);
        }
        return new Pair<>(thing, false);
    }

    /**
     * Create a Thing.
     *
     * @param thingName ThingName
     * @return Thing object
     */
    public Thing createThing(String thingName) {
        Thing newThing = Thing.of(thingName);
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
     *
     * @param thing Thing which is being updated
     * @return New Thing version
     */
    public Thing updateThing(Thing thing) {
        // TODO: this method should throw exceptions instead of returning
        //  null if the Thing doesn't exist or if the Thing hasn't been modified.
        Thing oldThing = getThingInternal(thing.getThingName());

        if (oldThing == null) {
            return null;
        }

        if (!thing.isModified()) {
            // Nothing to modify - return old object
            return thing;
        }

        Thing newThing = Thing.of(thing.getThingName(), thing.getAttachedCertificateIds());
        return storeThing(newThing);
    }

    private Thing getThingInternal(String thingName) {
        Optional<ThingV1DTO> thingV1 = runtimeConfig.getThingV1(thingName);
        return thingV1.map(this::dtoToThing).orElse(null);
    }

    private Thing storeThing(Thing thing) {
        runtimeConfig.putThing(thingToDto(thing));
        domainEvents.emit(new ThingUpdated(thing.getThingName(), 0)); // TODO: remove from event
        return thing;
    }

    /**
     * Gets all the things stored in the registry.
     */
    public Stream<Thing> getAllThings() {
        return runtimeConfig.getAllThingsV1().map(this::dtoToThing);
    }

    /**
     * Deletes a thing from the repository.
     *
     * @param thing thing to remove
     */
    public void deleteThing(Thing thing) {
        runtimeConfig.removeThingV1(thing.getThingName());
    }

    private Thing dtoToThing(ThingV1DTO dto) {
        Map<String, Instant> certIds = dto.getCertificates().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Instant.ofEpochMilli(entry.getValue())));
        return Thing.of(dto.getThingName(), certIds);
    }

    private ThingV1DTO thingToDto(Thing thing) {
        Map<String, Long> certIds = thing.getAttachedCertificateIds().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toEpochMilli()));
        return new ThingV1DTO(thing.getThingName(), certIds);
    }
}
