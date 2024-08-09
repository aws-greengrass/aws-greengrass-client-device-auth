/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.session.attribute.Attribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
public class Component implements AttributeProvider {
    private static final Map<String, DeviceAttribute> ATTRIBUTES =
            Collections.singletonMap(Attribute.COMPONENT.getName(), expr -> true);

    @Override
    public String getNamespace() {
        return Attribute.Namespaces.COMPONENT;
    }

    @Override
    public DeviceAttribute getDeviceAttribute(String attributeName) {
        return ATTRIBUTES.get(attributeName);
    }
}
