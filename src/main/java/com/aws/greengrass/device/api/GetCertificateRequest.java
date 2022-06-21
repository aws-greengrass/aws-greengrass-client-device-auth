/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.api;

import lombok.Value;

import java.util.function.Consumer;


@Value
public class GetCertificateRequest {
    private String serviceName;
    private GetCertificateRequestOptions certificateRequestOptions;
    private Consumer<CertificateUpdateEvent> certificateUpdateConsumer;
}
