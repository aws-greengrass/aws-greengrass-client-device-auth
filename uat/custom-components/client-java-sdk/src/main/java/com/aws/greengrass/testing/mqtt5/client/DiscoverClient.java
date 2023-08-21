/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import com.aws.greengrass.testing.mqtt.client.CoreDeviceDiscoverReply;
import com.aws.greengrass.testing.mqtt.client.CoreDeviceDiscoverRequest;
import com.aws.greengrass.testing.mqtt5.client.exceptions.DiscoverException;

/**
 * Interface of discovery client.
 */
public interface DiscoverClient {

    /**
     * Does discover of Core device broker.
     *
     * @param request the request
     * @return formatted gRPC response
     * @throws DiscoverException on errors
     */
    CoreDeviceDiscoverReply discoverCoreDevice(CoreDeviceDiscoverRequest request) throws DiscoverException;
}
