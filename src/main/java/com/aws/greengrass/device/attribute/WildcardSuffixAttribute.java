/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.attribute;

public class WildcardSuffixAttribute implements DeviceAttribute {
    private final String value;

    public WildcardSuffixAttribute(String attributeValue) {
        value = attributeValue;
    }

    @Override
    public boolean matches(String expr) {
        if (expr != null && expr.endsWith("*")) {
            return value.startsWith(expr.substring(0, expr.length() - 1));
        } else {
            return value.equals(expr);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
