/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateGenerator;
import lombok.Getter;
import lombok.NonNull;

import java.util.function.Consumer;

@Getter
public class GetCertificateRequestWithGenerator extends GetCertificateRequest {
    CertificateGenerator certificateGenerator;

    public GetCertificateRequestWithGenerator(String serviceName,
                                              GetCertificateRequestOptions certificateRequestOptions,
                                              Consumer<CertificateUpdateEvent> certificateUpdateConsumer,
                                              @NonNull CertificateGenerator certificateGenerator) {
        super(serviceName, certificateRequestOptions, certificateUpdateConsumer);
        this.certificateGenerator = certificateGenerator;
    }
}
