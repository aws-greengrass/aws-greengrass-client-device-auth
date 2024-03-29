/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class AuthorizeClientDeviceActionEvent implements DomainEvent {
    @Getter
    private AuthorizationStatus status;

    public enum AuthorizationStatus {
        SUCCESS,
        FAIL
    }
}
