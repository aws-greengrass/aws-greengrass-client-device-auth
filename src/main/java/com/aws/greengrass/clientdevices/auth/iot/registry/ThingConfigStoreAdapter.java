/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Adapter from Thing domain to Thing runtime configuration.
 * Thing configuration is stored as an opaque map to the runtime config store.
 * <p>
 * |---- runtime
 * |    |---- clientDeviceThings:
 * |          |---- thingName:
 * |                |---- version: "..."
 * |                |---- attachedCertificateIds: [...]
 * </p>
 */
public class ThingConfigStoreAdapter {
    private static final String ATTACHED_CERTIFICATE_IDS_KEY = "attachedCertificateIds";
    private static final String THING_VERSION_KEY = "version";

    private final RuntimeConfiguration runtimeConfig;

    @Inject
    protected ThingConfigStoreAdapter(RuntimeConfiguration runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * Retrieves client device thing from the runtime config store.
     * Returns an empty optional if the thing is not available in the config store
     *
     * @param thingName client device Thing name
     * @return optional of client device Thing
     */
    Optional<Thing> getThing(String thingName) {
        Map<String, Object> thingDetails = runtimeConfig.getClientDeviceThing(thingName);
        if (thingDetails.isEmpty()) {
            return Optional.empty();
        }

        Thing thing = Thing.of((int) thingDetails.get(THING_VERSION_KEY),
                thingName, (List<String>) thingDetails.get(ATTACHED_CERTIFICATE_IDS_KEY));

        return Optional.of(thing);
    }

    /**
     * Store (or update if available) client device thing in the runtime config store.
     *
     * @param thing client device thing
     * @throws IllegalArgumentException for invalid thing
     */
    void createOrUpdateThing(Thing thing) {
        if (thing == null) {
            throw new IllegalArgumentException("Thing cannot be null");
        }
        runtimeConfig.updateClientDeviceThing(thing.getThingName(),
                ImmutableMap.of(
                        THING_VERSION_KEY, thing.getVersion(),
                        ATTACHED_CERTIFICATE_IDS_KEY, thing.getAttachedCertificateIds()
                ));
    }
}
