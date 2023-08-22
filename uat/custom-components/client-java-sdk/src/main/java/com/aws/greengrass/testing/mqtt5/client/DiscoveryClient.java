/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt.client.CoreDeviceDiscoveryReply;
import com.aws.greengrass.testing.mqtt.client.CoreDeviceDiscoveryRequest;
import com.aws.greengrass.testing.mqtt5.client.exceptions.DiscoveryException;

/**
 * Interface of discovery client.
 */
public interface DiscoveryClient {

    /**
     * Does discovery of Core device broker.
     *
     * @param request the request
     * @return formatted gRPC response
     * @throws DiscoveryException on errors
     */
    CoreDeviceDiscoveryReply discoveryCoreDevice(CoreDeviceDiscoveryRequest request) throws DiscoveryException;
}
