/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.tes.LazyCredentialProvider;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.IotSdkClientFactory;
import com.aws.greengrass.util.exceptions.InvalidEnvironmentStageException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CertificateDescription;
import software.amazon.awssdk.services.iot.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.iot.model.DescribeCertificateResponse;

import java.net.URISyntaxException;
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
     * @throws InvalidEnvironmentStageException if environment stage is invalid
     * @throws URISyntaxException               if unable to build iot client
     */
    @Inject
    public CertificateDownloader(final LazyCredentialProvider credentialsProvider,
                                 final DeviceConfiguration deviceConfiguration)
            throws InvalidEnvironmentStageException, URISyntaxException {
        Region awsRegion = Region.of(Coerce.toString(deviceConfiguration.getAWSRegion()));
        IotSdkClientFactory.EnvironmentStage stage = IotSdkClientFactory.EnvironmentStage.fromString(
                Coerce.toString(deviceConfiguration.getEnvironmentStage()));
        iotClient = IotSdkClientFactory.getIotClient(awsRegion, stage, credentialsProvider);
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
    public List<String> batchDownloadDeviceCertificates(final List<String> certificateIds) {
        ArrayList<String> certificatePems = new ArrayList<>();

        // TODO: Retries
        // TODO: Handle exceptions
        // TODO: Replace with batch data plane API
        for (String certificateId : certificateIds) {
            certificatePems.add(downloadSingleDeviceCertificate(certificateId));
        }

        return certificatePems;
    }

    /**
     * Download single IoT device certificate.
     * @param certificateId Device certificateId to download
     * @return Certificate as PEM encoded string
     */
    public String downloadSingleDeviceCertificate(String certificateId) {
        DescribeCertificateRequest request = DescribeCertificateRequest
                .builder()
                .certificateId(certificateId)
                .build();

        DescribeCertificateResponse response = iotClient.describeCertificate(request);
        CertificateDescription description = response.certificateDescription();
        return description.certificatePem();
    }
}
