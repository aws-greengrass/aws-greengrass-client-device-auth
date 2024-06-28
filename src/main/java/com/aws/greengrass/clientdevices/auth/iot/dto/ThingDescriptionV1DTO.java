/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

@Value
@AllArgsConstructor
public class ThingDescriptionV1DTO {
    String thingName;
    Map<String, String> attributes;
    LocalDateTime lastUpdated;
}
