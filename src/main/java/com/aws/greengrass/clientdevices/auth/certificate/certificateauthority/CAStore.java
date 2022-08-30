/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.certificateauthority;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public interface CAStore {
    X509Certificate[] getCACertificateChain();

    PrivateKey getCAPrivateKey();

    X509Certificate getCACertificate();
}
