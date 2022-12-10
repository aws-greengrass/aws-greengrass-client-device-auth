/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session.events;

import com.aws.greengrass.clientdevices.auth.api.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class SessionCreationEvent implements DomainEvent {
    @Getter
    private SessionCreationStatus sessionCreationStatus;

    public enum SessionCreationStatus {
        SUCCESS,
        FAILURE
    }
}
