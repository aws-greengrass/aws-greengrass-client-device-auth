/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.dcm.certificate;

import com.aws.iot.evergreen.deployment.DeviceConfiguration;
import com.aws.iot.evergreen.tes.LazyCredentialProvider;
import com.aws.iot.evergreen.util.Coerce;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CertificateDescription;
import software.amazon.awssdk.services.iot.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.iot.model.DescribeCertificateResponse;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class CertificateDownloader {
    private final IotClient iotClient;

    /**
     * CertificateDownloader constructor.
     *
     * @param credentialsProvider AWS SDK credentials provider
     * @param deviceConfiguration Greengrass device configuration
     */
    @Inject
    public CertificateDownloader(final LazyCredentialProvider credentialsProvider,
                                 final DeviceConfiguration deviceConfiguration) {
        iotClient = IotClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(Coerce.toString(deviceConfiguration.getAWSRegion())))
                .build();
    }

    public CertificateDownloader(final IotClient iotClient) {
        this.iotClient = iotClient;
    }

    /**
     * Download device certificates from IoT Core.
     *
     * @param certificateIds List of device certificateIds to download
     * @return List of certificates as PEM encoded strings
     */
    // TODO: Revisit return type. List of strings is not sufficient (e.g. if a cert ID doesn't exist)
    public List<String> downloadDeviceCertificate(final List<String> certificateIds) {
        ArrayList<String> certificatePems = new ArrayList<>();

        // TODO: Retries
        // TODO: Handle exceptions
        // TODO: Replace with batch data plane API
        for (String certificateId : certificateIds) {
            DescribeCertificateRequest request = DescribeCertificateRequest
                    .builder()
                    .certificateId(certificateId)
                    .build();

            DescribeCertificateResponse response = iotClient.describeCertificate(request);
            CertificateDescription description = response.certificateDescription();
            certificatePems.add(description.certificatePem());
        }

        return certificatePems;
    }
}
