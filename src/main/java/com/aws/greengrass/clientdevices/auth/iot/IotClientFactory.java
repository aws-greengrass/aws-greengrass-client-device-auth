/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.IotSdkClientFactory;
import com.aws.greengrass.util.Utils;
import com.aws.greengrass.util.exceptions.InvalidEnvironmentStageException;
import software.amazon.awssdk.services.iot.IotClient;

import java.net.URISyntaxException;
import javax.inject.Inject;

public class IotClientFactory {

    private final DeviceConfiguration deviceConfiguration;

    @Inject
    public IotClientFactory(DeviceConfiguration deviceConfiguration) {
        this.deviceConfiguration = deviceConfiguration;
    }

    /**
     * Get an IoT Client.
     *
     * @return client
     * @throws DeviceConfigurationException never
     */
    public IotClient getClient() throws DeviceConfigurationException {
        try {
            String stage = Coerce.toString(deviceConfiguration.getEnvironmentStage());
            if (stage == null) {
                throw new DeviceConfigurationException("Environment stage not configured");
            }
            return IotSdkClientFactory.getIotClient(
                    getAwsRegion(deviceConfiguration),
                    IotSdkClientFactory.EnvironmentStage.fromString(stage)
            );
        } catch (URISyntaxException | InvalidEnvironmentStageException e) {
            throw new DeviceConfigurationException(e);
        }
    }

    private String getAwsRegion(DeviceConfiguration deviceConfiguration) throws DeviceConfigurationException {
        String awsRegion = Coerce.toString(deviceConfiguration.getAWSRegion());
        if (Utils.isEmpty(awsRegion)) {
            throw new DeviceConfigurationException("AWS region cannot be empty");
        }
        return awsRegion;
    }
}
