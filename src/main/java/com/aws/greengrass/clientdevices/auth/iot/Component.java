/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.StringLiteralAttribute;
import lombok.Value;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Value
public final class Component implements AttributeProvider {
    public static final String NAMESPACE = "Component";
    private final Map<String, DeviceAttribute> attributeMap;
    private final String componentName;

    public static Component of(String componentName) {
        return new Component(componentName);
    }

    private Component(String componentName) {
        Map<String, DeviceAttribute> attributes = new HashMap<>();
        attributes.put("componentName", new StringLiteralAttribute(componentName));
        attributes.put("component", expr -> true);
        this.attributeMap = Collections.unmodifiableMap(attributes);
        this.componentName = componentName;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public Map<String, DeviceAttribute> getDeviceAttributes() {
        return attributeMap;
    }
}
