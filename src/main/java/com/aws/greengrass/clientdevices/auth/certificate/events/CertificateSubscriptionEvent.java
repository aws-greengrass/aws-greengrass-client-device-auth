/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.events;

import com.aws.greengrass.clientdevices.auth.api.DomainEvent;
import com.aws.greengrass.clientdevices.auth.api.GetCertificateRequestOptions;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class CertificateSubscriptionEvent implements DomainEvent {
    @Getter
    private GetCertificateRequestOptions.CertificateType certificateType;
    @Getter
    private subscriptionStatus status;
    public enum subscriptionStatus {
        SUCCESS,
        FAIL
    }

    public CertificateSubscriptionEvent(GetCertificateRequestOptions.CertificateType certificateType,
                                        subscriptionStatus status) {
        this.certificateType = certificateType;
        this.status = status;
    }
}
