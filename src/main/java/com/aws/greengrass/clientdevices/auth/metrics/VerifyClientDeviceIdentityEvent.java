/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.metrics;

import com.aws.greengrass.clientdevices.auth.api.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class VerifyClientDeviceIdentityEvent implements DomainEvent {
    @Getter
    private VerificationStatus status;

    public enum VerificationStatus {
        SUCCESS,
        FAIL
    }
}
