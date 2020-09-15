/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.certificatemanager.certificate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CertificateDescription;
import software.amazon.awssdk.services.iot.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.iot.model.DescribeCertificateResponse;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class})
public class CertificateDownloaderTest {

    @Mock
    private IotClient mockIotClient;

    @BeforeEach
    public void setup() {
    }

    @Test
    public void GIVEN_cert_downloader_WHEN_download_single_certificate_THEN_return_single_certificate_pem() {
        CertificateDownloader certDownloader = new CertificateDownloader(mockIotClient);
        List<String> certificateIDs = new ArrayList<>();
        certificateIDs.add("ID1");

        when(mockIotClient.describeCertificate(any(DescribeCertificateRequest.class))).thenAnswer(i -> {
            DescribeCertificateRequest request = i.getArgument(0);
            DescribeCertificateResponse response = mock(DescribeCertificateResponse.class);
            CertificateDescription description = mock(CertificateDescription.class);
            when(response.certificateDescription()).thenReturn(description);
            when(description.certificatePem()).thenReturn(request.certificateId() + "-CERT");
            return response;
        });

        List<String> certificates = certDownloader.batchDownloadDeviceCertificates(certificateIDs);
        assertThat(certificates.get(0), equalTo("ID1-CERT"));
    }

    @Test
    public void GIVEN_cert_downloader_WHEN_download_multiple_certificates_THEN_return_multiple_certificate_pems() {
        CertificateDownloader certDownloader = new CertificateDownloader(mockIotClient);
        List<String> certificateIDs = new ArrayList<>();
        certificateIDs.add("ID1");
        certificateIDs.add("ID2");

        when(mockIotClient.describeCertificate(any(DescribeCertificateRequest.class))).thenAnswer(i -> {
            DescribeCertificateRequest request = i.getArgument(0);
            DescribeCertificateResponse response = mock(DescribeCertificateResponse.class);
            CertificateDescription description = mock(CertificateDescription.class);
            when(response.certificateDescription()).thenReturn(description);
            when(description.certificatePem()).thenReturn(request.certificateId() + "-CERT");
            return response;
        });

        List<String> certificates = certDownloader.batchDownloadDeviceCertificates(certificateIDs);
        assertThat(certificates.get(0), equalTo("ID1-CERT"));
        assertThat(certificates.get(1), equalTo("ID2-CERT"));
    }
}
