/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.tes.LazyCredentialProvider;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.DescribeThingRequest;

import java.util.Collections;
import java.util.Map;
import javax.inject.Inject;

public interface IotCoreClient {

    Map<String, String> getThingAttributes(String thingName) throws CloudServiceInteractionException;

    class Default implements IotCoreClient {

        private final DeviceConfiguration deviceConfiguration;
        private final IotClientFactory iotClientFactory;
        private final LazyCredentialProvider lazyCredentialProvider;

        @Inject
        Default(DeviceConfiguration deviceConfiguration,
                IotClientFactory iotClientFactory,
                LazyCredentialProvider lazyCredentialProvider) {
            this.deviceConfiguration = deviceConfiguration;
            this.iotClientFactory = iotClientFactory;
            this.lazyCredentialProvider = lazyCredentialProvider;
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        public Map<String, String> getThingAttributes(String thingName) throws CloudServiceInteractionException {
            try (IotClient client = iotClientFactory.getClient()) {
                Map<String, String> attributes = client.describeThing(DescribeThingRequest.builder()
                                .thingName(thingName)
                                .build())
                        .attributes();
                return attributes == null ? Collections.emptyMap() : attributes;
            } catch (DeviceConfigurationException e) {
                throw new CloudServiceInteractionException("Failed to construct IoT Core client", e);
            } catch (Exception e) {
                throw new CloudServiceInteractionException(
                        String.format("Failed to get %s thing attributes", thingName), e);
            }
        }
    }
}
