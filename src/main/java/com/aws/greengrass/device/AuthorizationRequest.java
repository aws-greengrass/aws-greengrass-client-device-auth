/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class AuthorizationRequest {
    @NonNull String operation;
    @NonNull String resource;
    @NonNull String sessionId;
    String clientId;
}
