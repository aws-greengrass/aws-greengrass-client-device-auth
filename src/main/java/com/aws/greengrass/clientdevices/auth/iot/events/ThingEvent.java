/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.events;

import com.aws.greengrass.clientdevices.auth.api.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
public class ThingEvent implements DomainEvent {
    @Getter
    private ThingEventType eventType;
    @Getter
    private String thingName;
    @Getter
    private List<String> attachedCertificateIds;

    public enum ThingEventType {
        THING_UPDATED
    }
}
