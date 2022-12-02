/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.events;

import com.aws.greengrass.clientdevices.auth.api.DomainEvent;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import lombok.Getter;

public class CertificateSubscriptionEvent implements DomainEvent {
    @Getter
    private GetCertificateRequestOptions.CertificateType certificateType;
    @Getter
    private SubscriptionStatus status;

    public enum SubscriptionStatus {
        SUCCESS,
        FAIL
    }

    public CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType certificateType,
                                        SubscriptionStatus status) {
        this.certificateType = certificateType;
        this.status = status;
    }
}
