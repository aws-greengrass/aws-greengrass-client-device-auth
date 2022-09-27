/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.events;

import com.aws.greengrass.clientdevices.auth.api.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class ThingUpdated implements DomainEvent {
    @Getter
    private String thingName;
    @Getter
    private int thingVersion;
}
