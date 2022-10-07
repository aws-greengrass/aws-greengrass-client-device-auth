/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@Value
@AllArgsConstructor
public class ThingV1DTO {
    private String thingName;
    // Map from CertificateID to the time this certificate was known to be attached to the Thing
    //  Timestamp is in milliseconds since epoch
    private Map<String, Long> certificates;
}
