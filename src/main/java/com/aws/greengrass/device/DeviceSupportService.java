/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.config.Topic;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.config.WhatHappened;
import com.aws.greengrass.dependency.ImplementsService;
import com.aws.greengrass.device.configuration.GroupConfiguration;
import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.lifecyclemanager.PluginService;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;

@ImplementsService(name = DeviceSupportService.DEVICE_SUPPORT_SERVICE_NAME)
@SuppressWarnings("PMD.UnusedPrivateField")
public class DeviceSupportService extends PluginService {
    public static final String DEVICE_SUPPORT_SERVICE_NAME = "aws.greengrass.DeviceSupport";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);

    @Inject
    private GroupManager groupManager;

    @Inject
    private SessionManager sessionManager;

    /**
     * Constructor.
     *
     * @param topics Root Configuration topic for this service
     */
    @Inject
    public DeviceSupportService(Topics topics) {
        super(topics);
        topics.lookup(CONFIGURATION_CONFIG_KEY).subscribe(this::handleConfigurationChange);
    }

    private void handleConfigurationChange(WhatHappened whatHappened, Topic configurationTopic) {
        if (configurationTopic == null) {
            logger.atInfo().kv("service", DEVICE_SUPPORT_SERVICE_NAME).kv("event", whatHappened)
                    .log("No group configuration");
            groupManager.setGroupConfiguration(GroupConfiguration.builder().build());
            return;
        }

        try {
            groupManager.setGroupConfiguration(
                    OBJECT_MAPPER.convertValue(configurationTopic.toPOJO(), GroupConfiguration.class));
        } catch (IllegalArgumentException e) {
            logger.atError().kv("service", DEVICE_SUPPORT_SERVICE_NAME).kv("event", whatHappened)
                    .kv("node", configurationTopic.getFullName()).kv("value", configurationTopic).setCause(e)
                    .log("Unable to parse group configuration");
            serviceErrored(e);
        }
    }
}
