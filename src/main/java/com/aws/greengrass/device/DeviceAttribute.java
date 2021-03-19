/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import lombok.Getter;

public class DeviceAttribute {
    @Getter
    private final String value;

    public DeviceAttribute(String attributeValue) {
        value = attributeValue;
    }
}
