/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.attribute;

public class StringLiteralAttribute implements DeviceAttribute {
    private final String value;

    public StringLiteralAttribute(String attributeValue) {
        value = attributeValue;
    }

    @Override
    public boolean matches(String expr) {
        return value.equals(expr);
    }

    @Override
    public String toString() {
        return value;
    }
}
