/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.config.Node;
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

    private final GroupManager groupManager;

    private final SessionManager sessionManager;

    private final Topics configurationTopics;

    /**
     * Constructor.
     *
     * @param topics         Root Configuration topic for this service
     * @param groupManager   Group configuration management
     * @param sessionManager Session management
     */
    @Inject
    public DeviceSupportService(Topics topics, GroupManager groupManager, SessionManager sessionManager) {
        super(topics);
        this.groupManager = groupManager;
        this.sessionManager = sessionManager;

        //handleConfiguration
        this.configurationTopics = topics.lookupTopics(CONFIGURATION_CONFIG_KEY);
        this.configurationTopics.subscribe(this::handleConfigurationChange);
    }

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private void handleConfigurationChange(WhatHappened whatHappened, Node childNode) {
        try {
            groupManager.setGroupConfiguration(
                    OBJECT_MAPPER.convertValue(configurationTopics.toPOJO(), GroupConfiguration.class));
        } catch (IllegalArgumentException e) {
            logger.atError().kv("service", DEVICE_SUPPORT_SERVICE_NAME).kv("event", whatHappened)
                    .kv("node", configurationTopics.getFullName()).kv("value", configurationTopics).setCause(e)
                    .log("Unable to parse group configuration");
        }
    }
}
