/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.dto.ThingV1DTO;
import com.aws.greengrass.clientdevices.auth.iot.events.ThingUpdated;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class ThingRegistry {
    private final IotAuthClient iotAuthClient;
    private final DomainEvents domainEvents;
    private final RuntimeConfiguration runtimeConfig;

    /**
     * Construct Thing registry.
     *
     * @param iotAuthClient IoT auth client
     * @param domainEvents Domain events
     * @param runtimeConfig Runtime configuration store
     */
    @Inject
    public ThingRegistry(IotAuthClient iotAuthClient,
                         DomainEvents domainEvents,
                         RuntimeConfiguration runtimeConfig) {
        this.iotAuthClient = iotAuthClient;
        this.domainEvents = domainEvents;
        this.runtimeConfig = runtimeConfig;
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
     * @param thing Thing which is being updated
     * @return      New Thing version
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
     * Returns whether the Thing is associated to the given IoT Certificate.
     * Returns valid locally registered result when IoT Core cannot be reached.
     *
     * @param thing IoT Thing
     * @param certificate IoT Certificate
     * @return whether thing is attached to the certificate
     * @throws CloudServiceInteractionException when thing <-> certificate association cannot be verified
     */
    public boolean isThingAttachedToCertificate(Thing thing, Certificate certificate) {
        // If we have a local cache hit, then return true. Else, go to cloud
        if (thing.isCertificateAttached(certificate.getCertificateId())) {
            return true;
        }

        if (iotAuthClient.isThingAttachedToCertificate(thing, certificate)) {
            thing.attachCertificate(certificate.getCertificateId());
            updateThing(thing);
            return true;
        } else {
            thing.detachCertificate(certificate.getCertificateId());
            updateThing(thing);
            return false;
        }
    }

    private Thing dtoToThing(ThingV1DTO dto) {
        Map<String, Instant> certIds = dto.getCertificates().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Instant.ofEpochMilli(entry.getValue())
                ));
        return Thing.of(dto.getThingName(), certIds);
    }

    private ThingV1DTO thingToDto(Thing thing) {
        Map<String, Long> certIds = thing.getAttachedCertificateIds().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toEpochMilli()
                ));
        return new ThingV1DTO(thing.getThingName(), certIds);
    }
}
