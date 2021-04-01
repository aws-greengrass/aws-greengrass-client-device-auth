/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

import com.aws.greengrass.device.attribute.AttributeProvider;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.attribute.StringLiteralAttribute;

import java.util.Collections;
import java.util.Map;

public class Thing implements AttributeProvider {
    public static final String NAMESPACE = "Thing";

    private final String thingName;

    public Thing(String thingName) {
        this.thingName = thingName;
    }

    public boolean isCertificateAttached(Certificate certificate) {
        // TODO
        return true;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public Map<String, DeviceAttribute> getDeviceAttributes() {
        return Collections.singletonMap("ThingName", new StringLiteralAttribute(thingName));
    }
}
