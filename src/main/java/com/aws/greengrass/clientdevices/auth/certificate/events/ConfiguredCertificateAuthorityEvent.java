/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.events;

import com.aws.greengrass.clientdevices.auth.api.DomainEvent;
import lombok.Getter;

import java.security.cert.Certificate;

public class ConfiguredCertificateAuthorityEvent implements DomainEvent {
    @Getter
    private Certificate[] caCertificates;

    public ConfiguredCertificateAuthorityEvent(Certificate... caCertificates) {
        this.caCertificates = caCertificates;
    }
}
