/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.dto;

import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import lombok.AllArgsConstructor;
import lombok.Value;


@Value
@AllArgsConstructor
public class VerifyThingAttachedToCertificateDTO {
    Thing thing;
    Certificate certificate;
}

