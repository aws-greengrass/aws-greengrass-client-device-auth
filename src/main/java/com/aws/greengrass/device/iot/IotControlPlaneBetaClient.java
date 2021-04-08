/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.tes.LazyCredentialProvider;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.IotSdkClientFactory;
import com.aws.greengrass.util.exceptions.InvalidEnvironmentStageException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.iot.model.DescribeCertificateResponse;
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsRequest;
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsResponse;

import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class IotControlPlaneBetaClient implements IotAuthClient {
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
    public IotControlPlaneBetaClient(final LazyCredentialProvider credentialsProvider,
                                     final DeviceConfiguration deviceConfiguration)
            throws InvalidEnvironmentStageException, URISyntaxException {
        Region awsRegion = Region.of(Coerce.toString(deviceConfiguration.getAWSRegion()));
        IotSdkClientFactory.EnvironmentStage stage = IotSdkClientFactory.EnvironmentStage.fromString(
                Coerce.toString(deviceConfiguration.getEnvironmentStage()));
        iotClient = IotSdkClientFactory.getIotClient(awsRegion, stage, credentialsProvider);
    }

    IotControlPlaneBetaClient(final IotClient iotClient) {
        this.iotClient = iotClient;
    }

    @Override
    public String getActiveCertificateId(String certificatePem) {
        // TODO
        return null;
    }

    @Override
    public boolean isThingAttachedToCertificate(Thing thing, Certificate certificate) {
        List<String> attachedIds = listThingCertificatePrincipals(thing.getThingName());

        for (String certificateId : attachedIds) {
            String iotCertificate = downloadSingleDeviceCertificate(certificateId);
            if (iotCertificate.equals(certificate.getCertificatePem())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Download single IoT device certificate.
     *
     * @param certificateId Device certificateId to download
     * @return Certificate as PEM encoded string
     */
    public String downloadSingleDeviceCertificate(String certificateId) {
        DescribeCertificateRequest request = DescribeCertificateRequest
                .builder()
                .certificateId(certificateId)
                .build();

        DescribeCertificateResponse response = iotClient.describeCertificate(request);
        return response.certificateDescription().certificatePem();
    }

    /**
     * List certificate principals associated with a given Thing.
     *
     * @param thingName Iot Thing Name
     * @return List of certificate principals
     */
    public List<String> listThingCertificatePrincipals(String thingName) {
        ListThingPrincipalsRequest request = ListThingPrincipalsRequest
                .builder()
                .thingName(thingName)
                .build();

        ListThingPrincipalsResponse response = iotClient.listThingPrincipals(request);
        return response.principals().stream()
                .filter(p -> p.contains("cert"))
                .map(p -> p.split("/")[1])
                .collect(Collectors.toList());
    }
}
