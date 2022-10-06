/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.dto;

import com.aws.greengrass.clientdevices.auth.api.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@Value
@AllArgsConstructor
public class ThingV1 implements DomainEvent {
    private String thingName;
    private Map<String, Long> certificates;
}
