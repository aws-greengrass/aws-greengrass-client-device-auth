/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.api;


import lombok.Value;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

@Value
public class CertificateUpdateEvent {
    KeyPair keyPair;
    X509Certificate certificate;
    X509Certificate[] caCertificates;
}
