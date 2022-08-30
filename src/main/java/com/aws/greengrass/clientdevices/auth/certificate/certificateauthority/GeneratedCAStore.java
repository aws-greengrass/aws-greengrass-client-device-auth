/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.certificate.certificateauthority;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class GeneratedCAStore implements CAStore {

    @Override
    public X509Certificate[] getCACertificateChain() {
        return new X509Certificate[0];
    }

    @Override
    public PrivateKey getCAPrivateKey() {
        return null;
    }

    @Override
    public X509Certificate getCACertificate() {
        return null;
    }
}

