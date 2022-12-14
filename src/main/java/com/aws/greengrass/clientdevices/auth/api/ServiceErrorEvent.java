/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class ServiceErrorEvent implements DomainEvent {
    @Getter
    private Exception e;
    @Getter
    private String errorMessage;
}
