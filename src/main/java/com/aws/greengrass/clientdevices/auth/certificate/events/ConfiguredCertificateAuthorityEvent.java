/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.events;

import lombok.Getter;

import java.security.cert.Certificate;

public class ConfiguredCertificateAuthorityEvent {
    @Getter
    private Certificate[] caCertificates;

    public ConfiguredCertificateAuthorityEvent(Certificate... caCertificates) {
        this.caCertificates = caCertificates;
    }
}
