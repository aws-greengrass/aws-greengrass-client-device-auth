/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.Consumer;

@Getter
@AllArgsConstructor
public class GetCertificateRequest {
    private String serviceName;
    private GetCertificateRequestOptions certificateRequestOptions;
    private Consumer<CertificateUpdateEvent> certificateUpdateConsumer;
}
