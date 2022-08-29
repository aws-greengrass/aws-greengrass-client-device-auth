/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class CertificateEntry {
    private Instant validTill;
    private String certificateHash;
    private String iotCertificateId;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CertificateEntry)) {
            return false;
        }
        return this.getIotCertificateId()
                .equals(((CertificateEntry) obj).iotCertificateId);
    }

    @Override
    public int hashCode() {
        return this.getIotCertificateId().hashCode();
    }
}
