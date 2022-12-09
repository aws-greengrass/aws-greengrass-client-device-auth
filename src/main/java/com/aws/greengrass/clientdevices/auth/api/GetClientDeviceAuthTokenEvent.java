/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class GetClientDeviceAuthTokenEvent implements DomainEvent {
    @Getter
    private GetAuthTokenStatus getAuthTokenStatus;

    public enum GetAuthTokenStatus {
        SUCCESS,
        FAILURE
    }
}
