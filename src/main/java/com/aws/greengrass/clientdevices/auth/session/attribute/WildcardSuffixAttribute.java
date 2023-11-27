/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session.attribute;

import lombok.NonNull;

public class WildcardSuffixAttribute implements DeviceAttribute {
    private final String value;

    public WildcardSuffixAttribute(String attributeValue) {
        value = attributeValue;
    }

    @Override
    public boolean matches(@NonNull String expr) {
        if (expr.length() > 1 && expr.startsWith("*") && expr.endsWith("*")) {
            return value.contains(expr.substring(1, expr.length() -1));
        } else if (expr.startsWith("*")) {
            return value.endsWith(expr.substring(1));
        } else if (expr.endsWith("*")) {
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
