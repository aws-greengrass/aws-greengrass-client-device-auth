/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm.certgeneration;

import com.aws.iot.evergreen.gcmclient.GCMClient;

import java.security.cert.X509Certificate;

@SuppressWarnings("PMD.UnusedPrivateField") // TODO: This should be removed once we start using the GCM Client
public class CertGenerator {
    private final GCMClient gcmClient;

    public CertGenerator(final GCMClient gcmClient) {
        this.gcmClient = gcmClient;
    }

    public X509Certificate generateNewCertificate(ConnectivityInfoForCertGen info) {
        // TODO: Add gen logic
        return null;
    }
}
