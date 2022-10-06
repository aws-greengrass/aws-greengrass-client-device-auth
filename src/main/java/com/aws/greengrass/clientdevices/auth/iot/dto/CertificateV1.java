/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.dto;

import com.aws.greengrass.clientdevices.auth.api.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class CertificateV1 implements DomainEvent {
    // IMPORTANT! New statuses must be added to the END of this list
    //  so that we can safely deserialize from storage
    public enum Status {
        UNKNOWN, // MUST be first!
        ACTIVE
    }

    private String certificateId;
    private Status status;
    private Long statusUpdated;
}
