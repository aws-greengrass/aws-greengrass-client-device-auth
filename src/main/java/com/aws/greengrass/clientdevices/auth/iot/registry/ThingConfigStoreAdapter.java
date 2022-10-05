/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;
import software.amazon.awssdk.utils.ImmutableMap;

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
    public static final String THING_VERSION_KEY = "version";
    public static final String ATTACHED_CERTIFICATE_IDS_KEY = "attachedCertificateIds";
    private final RuntimeConfiguration runtimeConfig;

    @Inject
    protected ThingConfigStoreAdapter(RuntimeConfiguration runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * Creates client device thing in the runtime config store.
     *
     * @param thing client device thing
     * @throws IllegalArgumentException for invalid thing
     */
    void createThing(Thing thing) {
        if (thing == null) {
            throw new IllegalArgumentException("Thing cannot be null");
        }
        runtimeConfig.createOrReplaceClientDeviceThing(thing.getThingName(),
                ImmutableMap.of(
                        THING_VERSION_KEY, thing.getVersion(),
                        ATTACHED_CERTIFICATE_IDS_KEY, thing.getAttachedCertificateIds()
                ));
    }

    /**
     * Retrieves client device thing from the runtime config store.
     * Returns an empty optional if the thing is not available in the config store
     *
     * @param thingName client device Thing name
     * @return optional of client device Thing
     */
    Optional<Thing> getThing(String thingName) {
        Topics thingDetails = runtimeConfig.getClientDeviceThing(thingName);
        if (thingDetails == null || thingDetails.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(getThingFromConfig(thingName, thingDetails));
    }

    private Thing getThingFromConfig(String thingName, Topics thingDetails) {
        // thing version defaults to 0 if NumberFormatException while coercing
        return Thing.of(Coerce.toInt(thingDetails.find(THING_VERSION_KEY)), thingName,
                Coerce.toStringList(thingDetails.find(ATTACHED_CERTIFICATE_IDS_KEY)));
    }
}
