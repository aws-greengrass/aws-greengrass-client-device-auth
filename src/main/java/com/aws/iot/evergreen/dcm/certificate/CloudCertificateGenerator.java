/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm.certificate;

import com.aws.iot.evergreen.gcmclient.GCMClient;
import com.aws.iot.evergreen.gcmclient.GCMClientException;
import com.aws.iot.evergreen.gcmclient.GetCertificateRequest;
import com.aws.iot.evergreen.gcmclient.GetCertificateResponse;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class CloudCertificateGenerator {
    private final GCMClient gcmClient;

    public CloudCertificateGenerator(final GCMClient gcmClient) {
        this.gcmClient = gcmClient;
    }

    /**
     * Generates an X509 Certificate.
     *
     * @param csr PEM encoded CSR
     * @return A PEM encoded X509 Certificate
     * @throws GCMClientException   On invalid input or network failure
     * @throws CertificateException If returned certificate fails to parse
     */
    public X509Certificate generateNewCertificate(String csr) throws GCMClientException, CertificateException {
        // TODO: Add retry logic
        GetCertificateRequest certificateRequest = new GetCertificateRequest(csr);
        GetCertificateResponse certificateResponse = gcmClient.getCertificate(certificateRequest);
        String certificateString = certificateResponse.getCertificate();

        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificateString.getBytes()));
    }
}
