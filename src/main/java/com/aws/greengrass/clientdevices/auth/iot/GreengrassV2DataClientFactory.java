/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.componentmanager.ClientConfigurationUtils;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Utils;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClientBuilder;

import java.net.URI;
import javax.inject.Inject;

/**
 * Provides GG service clients built specifically for CDA use case. Ideally this factory should maintain the lifecycle
 * of its clients, but for now its upto the consumer of the clients to maintain their lifecycle.
 */
public class GreengrassV2DataClientFactory {
    private final DeviceConfiguration deviceConfiguration;

    /**
     * Construct Greengrass Client Factory.
     *
     * @param deviceConfiguration GG Core Device Configuration
     */
    @Inject
    public GreengrassV2DataClientFactory(DeviceConfiguration deviceConfiguration) {
        this.deviceConfiguration = deviceConfiguration;
    }

    protected ApacheHttpClient.Builder getHttpClientBuilder() {
        return ClientConfigurationUtils.getConfiguredClientBuilder(deviceConfiguration);
    }

    /**
     * Provides a new GG v2 Data client without implicit retry policy. Consumer should handle retries when appropriate
     * and maintain the lifecycle of the client. The Nucleus provided V2DataClient has a built in retry policy which
     * cannot be overridden.
     *
     * @return GreengrassV2DataClient
     * @throws DeviceConfigurationException if configured AWS region is empty
     */
    public GreengrassV2DataClient getClient() throws DeviceConfigurationException {
        String awsRegion = getAwsRegion(deviceConfiguration);
        String ggServiceEndpoint = ClientConfigurationUtils.getGreengrassServiceEndpoint(deviceConfiguration);
        ApacheHttpClient.Builder httpClient = getHttpClientBuilder();

        GreengrassV2DataClientBuilder clientBuilder =
                GreengrassV2DataClient.builder().credentialsProvider(AnonymousCredentialsProvider.create())
                        .httpClient(httpClient.build()).overrideConfiguration(
                                ClientOverrideConfiguration.builder().retryPolicy(RetryPolicy.none()).build());

        clientBuilder.region(Region.of(awsRegion));
        clientBuilder.endpointOverride(URI.create(ggServiceEndpoint));
        return clientBuilder.build();
    }

    private String getAwsRegion(DeviceConfiguration deviceConfiguration) throws DeviceConfigurationException {
        String awsRegion = Coerce.toString(deviceConfiguration.getAWSRegion());
        if (Utils.isEmpty(awsRegion)) {
            throw new DeviceConfigurationException("AWS region cannot be empty");
        }
        return awsRegion;
    }

}
