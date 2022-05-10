/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.util;

import com.aws.greengrass.deployment.exceptions.DeviceConfigurationException;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;

public final class ClientUtil {

    private ClientUtil() {
    }

    /**
     * Initializes and returns GreengrassV2DataClient.
     * @param factory GreengrassServiceClientFactory
     * @throws DeviceConfigurationException when fails to get GreengrassV2DataClient.
     */
    public static GreengrassV2DataClient fetchGreengrassV2DataClient(GreengrassServiceClientFactory factory)
            throws DeviceConfigurationException {
        GreengrassV2DataClient client = factory.getGreengrassV2DataClient();
        if (client == null) {
            String errorMessage =
                    factory.getConfigValidationError() == null
                            ? "Could not get GreengrassV2DataClient." : factory.getConfigValidationError();
            throw new DeviceConfigurationException(errorMessage);
        }
        return client;
    }
}
