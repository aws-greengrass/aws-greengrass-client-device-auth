/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

import com.aws.greengrass.device.attribute.AttributeProvider;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.attribute.WildcardSuffixAttribute;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

public class Thing implements AttributeProvider {
    public static final String NAMESPACE = "Thing";
    private static final String thingNamePattern = "[a-zA-Z0-9\\-_:]+";

    @Getter
    private final String thingName;

    /**
     * Constructor.
     * @param thingName AWS IoT ThingName
     * @throws IllegalArgumentException If the given ThingName contains illegal characters
     */
    public Thing(String thingName) {
        if (!Pattern.matches(thingNamePattern, thingName)) {
            throw new IllegalArgumentException("Invalid ThingName");
        }
        this.thingName = thingName;
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
