/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cisclient;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class GetConnectivityInfoRequest {
    @Getter
    private String thingName;
}
