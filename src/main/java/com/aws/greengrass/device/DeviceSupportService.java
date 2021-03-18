/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.ImplementsService;
import com.aws.greengrass.lifecyclemanager.PluginService;

import javax.inject.Inject;

@ImplementsService(name = DeviceSupportService.DEVICE_SUPPORT_SERVICE_NAME)
public class DeviceSupportService extends PluginService {
    public static final String DEVICE_SUPPORT_SERVICE_NAME = "aws.greengrass.DeviceSupport";

    /**
     * Constructor.
     *
     * @param topics             Root Configuration topic for this service
     */
    @Inject
    public DeviceSupportService(Topics topics) {
        super(topics);
    }
}
