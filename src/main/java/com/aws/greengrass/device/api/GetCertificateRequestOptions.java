/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.api;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class GetCertificateRequestOptions {
    private CertificateType certificateType;

    public enum CertificateType {
        SERVER,
        CLIENT
    }
}
