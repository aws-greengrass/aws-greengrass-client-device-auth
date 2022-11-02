/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.componentmanager.ClientConfigurationUtils;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.tes.LazyCredentialProvider;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import com.aws.greengrass.util.IotSdkClientFactory;
import com.aws.greengrass.util.RegionUtils;
import com.aws.greengrass.util.Utils;
import com.aws.greengrass.util.exceptions.InvalidEnvironmentStageException;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2Client;
import software.amazon.awssdk.services.greengrassv2.GreengrassV2ClientBuilder;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClientBuilder;

import java.net.URI;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Provides GG service clients built specifically for CDA use case.
 * Ideally this factory should maintain the lifecycle of its clients,
 * but for now its upto the consumer of the clients to maintain their lifecycle.
 */
public class GreengrassClientFactory {
    private static final Logger logger = LogManager.getLogger(GreengrassClientFactory.class);
    private final GreengrassServiceClientFactory greengrassServiceClientFactory;
    private final LazyCredentialProvider lazyCredentialProvider;

    /**
     * Construct GG V2 Data Client Factory.
     *
     * @param greengrassServiceClientFactory GG Service Client Factory to retrieve the latest device-config
     * @param lazyCredentialProvider Credential provider for the client
     */
    @Inject
    public GreengrassClientFactory(GreengrassServiceClientFactory greengrassServiceClientFactory,
                                   LazyCredentialProvider lazyCredentialProvider) {
        this.greengrassServiceClientFactory = greengrassServiceClientFactory;
        this.lazyCredentialProvider = lazyCredentialProvider;
    }

    /**
     * Provides a new GG v2 client.
     * Its upto the caller to maintain the lifecycle of the client.
     * (TODO: This should ideally exist in Nucleus's GreengrassServiceClientFactory)
     *
     * @return GreengrassV2Client
     */
    public GreengrassV2Client getGGV2Client()  {
        DeviceConfiguration deviceConfiguration = getDeviceConfiguration();
        ApacheHttpClient.Builder httpClient = ClientConfigurationUtils
                .getConfiguredClientBuilder(deviceConfiguration);

        String awsRegion = Coerce.toString(deviceConfiguration.getAWSRegion());
        GreengrassV2ClientBuilder clientBuilder = GreengrassV2Client.builder()
                .httpClient(httpClient.build())
                .credentialsProvider(lazyCredentialProvider)
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder().retryPolicy(RetryMode.STANDARD).build());

        if (Utils.isEmpty(awsRegion)) {
            return clientBuilder.build();
        }
        clientBuilder.region(Region.of(awsRegion));
        getGreengrassServiceEndpoint(deviceConfiguration, awsRegion)
                .ifPresent(endpoint -> clientBuilder.endpointOverride(URI.create(endpoint)));

        return clientBuilder.build();
    }

    /**
     * Provides a new GG v2 Data client without implicit retry policy.
     * Consumer should handle retries when appropriate and maintain the lifecycle of the client.
     * The Nucleus provided V2DataClient has a built in retry policy which cannot be overridden.
     *
     * @return GreengrassV2DataClient
     */
    public GreengrassV2DataClient getGGV2DataClient()  {
        DeviceConfiguration deviceConfiguration = getDeviceConfiguration();
        ApacheHttpClient.Builder httpClient = ClientConfigurationUtils
                .getConfiguredClientBuilder(deviceConfiguration);

        String awsRegion = Coerce.toString(deviceConfiguration.getAWSRegion());
        GreengrassV2DataClientBuilder clientBuilder = GreengrassV2DataClient.builder()
                .credentialsProvider(lazyCredentialProvider)
                .httpClient(httpClient.build())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryPolicy(RetryPolicy.none()).build());

        if (Utils.isEmpty(awsRegion)) {
            return clientBuilder.build();
        }
        clientBuilder.region(Region.of(awsRegion));
        getGreengrassServiceEndpoint(deviceConfiguration, awsRegion)
                .ifPresent(endpoint -> clientBuilder.endpointOverride(URI.create(endpoint)));

        return clientBuilder.build();
    }

    /**
     * Provides latest Greengrass Core device configuration.
     *
     * @return Core device configuration
     */
    public DeviceConfiguration getDeviceConfiguration() {
        return greengrassServiceClientFactory.getDeviceConfiguration();
    }

    private Optional<String> getGreengrassServiceEndpoint(DeviceConfiguration deviceConfiguration,
                                                          String awsRegion) {
        try {
            String environment = Coerce.toString(deviceConfiguration.getEnvironmentStage());
            String endpoint = RegionUtils.getGreengrassControlPlaneEndpoint(
                    awsRegion, IotSdkClientFactory.EnvironmentStage.fromString(environment));
            return Optional.ofNullable(endpoint);
        } catch (InvalidEnvironmentStageException e) {
            logger.atError().cause(e).log("Failed to configure greengrass service endpoint for GG v2 client");
        }
        return Optional.empty();
    }

}
