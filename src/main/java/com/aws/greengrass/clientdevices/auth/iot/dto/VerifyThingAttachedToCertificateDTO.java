/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Value;


@Value
@AllArgsConstructor
public class VerifyThingAttachedToCertificateDTO {
    String thingName;
    String certificateId;
}

