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
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.Thing;
import com.aws.greengrass.lifecyclemanager.PluginService;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;

import static com.aws.greengrass.componentmanager.KernelConfigResolver.CONFIGURATION_CONFIG_KEY;

@ImplementsService(name = DeviceSupportService.DEVICE_SUPPORT_SERVICE_NAME)
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

    /**
     * determine device operation authorization.
     * @param operation operation in the form of 'service:action'
     * @param resource  resource in the form of 'service:resourceType:resourceName'
     * @param sessionId session id for identifying previous created session
     * @param clientId device client id, is thing name if device has registered with IoT core
     * @return if device is authorized
     * @throws AuthorizationException if session not existed or expired
     */
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public boolean canDevicePerform(String operation, String resource, String sessionId, String clientId)
            throws AuthorizationException {
        Session session = sessionManager.findSession(sessionId);
        if (session == null) {
            throw new AuthorizationException(String.format("session %s isn't existed or expired", sessionId));
        }

        Certificate certificate = (Certificate) session.get(Certificate.NAMESPACE);
        Thing thing = new Thing(clientId);
        // if thing name is already cached, proceed;
        // otherwise validate thing name with certificate, then cache thing name
        session.computeIfAbsent(thing.getNamespace(), (k) -> thing.isCertificateAttached(certificate) ? thing : null);
        return PermissionEvaluationUtils
                .isAuthorize(operation, resource, groupManager.getApplicablePolicyPermissions(session));
    }
}
