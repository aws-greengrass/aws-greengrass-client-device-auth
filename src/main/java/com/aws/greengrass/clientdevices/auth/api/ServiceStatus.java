/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import lombok.Builder;
import lombok.Value;

import java.util.Date;

@Builder
@Value
public class ServiceStatus {
    Boolean online;
    String brokerAddress; // TODO support multiple
}
