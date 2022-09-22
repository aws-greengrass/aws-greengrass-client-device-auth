/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.WildcardSuffixAttribute;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * This is a value object representing an IoT Thing at specific point in time.
 * It is **NOT** updated when the local Thing Registry is updated, or when
 * changes to this Thing are made in IoT Core.
 */
@Value
public class Thing implements AttributeProvider {
    public static final String NAMESPACE = "Thing";
    private static final String thingNamePattern = "[a-zA-Z0-9\\-_:]+";

    String thingName;
    List<String> attachedCertificateIds;

    /**
     * Create a Thing object with no attached certificates.
     *
     * @param thingName AWS IoT ThingName
     */
    public Thing(String thingName) {
        this(thingName, null);
    }

    /**
     * Create a Thing object with the provided attached certificate IDs.
     *
     * @param thingName      AWS IoT ThingName
     * @param certificateIds AWS IoT Certificate IDs
     * @throws IllegalArgumentException If the given ThingName contains illegal characters
     */
    public Thing(String thingName, List<String> certificateIds) {
        if (!Pattern.matches(thingNamePattern, thingName)) {
            throw new IllegalArgumentException("Invalid thing name. The thing name must match \"[a-zA-Z0-9\\-_:]+\".");
        }
        this.thingName = thingName;
        this.attachedCertificateIds = certificateIds;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public Map<String, DeviceAttribute> getDeviceAttributes() {
        return Collections.singletonMap("ThingName", new WildcardSuffixAttribute(thingName));
    }
}
