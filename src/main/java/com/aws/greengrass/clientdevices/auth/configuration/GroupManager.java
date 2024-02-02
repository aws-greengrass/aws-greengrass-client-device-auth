/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.session.Session;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton manager class for managing device group roles and retrieving permissions associated with Sessions. To
 * determine device permissions, the GroupManager first determines which device groups a Session belongs to, and then
 * merges device group permissions.
 */
public class GroupManager {
    private final AtomicReference<GroupConfiguration> groupConfigurationRef = new AtomicReference<>();

    public void setGroupConfiguration(GroupConfiguration groupConfiguration) {
        groupConfigurationRef.set(groupConfiguration);
    }

    /**
     * find applicable policy permissions to evaluate for the given device request.
     *
     * @param session session used to retrieve cached device attributes
     * @return Map of group name to applicable permission
     */
    public Map<String, Set<Permission>> getApplicablePolicyPermissions(Session session) {
        GroupConfiguration config = groupConfigurationRef.get();
        if (config == null) {
            return Collections.emptyMap();
        }
        Map<String, Set<Permission>> groupPermissions = new HashMap<>();
        for (Map.Entry<String, GroupDefinition> entry : config.getDefinitions().entrySet()) {
            if (entry.getValue().containsClientDevice(session)) {
                Set<Permission> permissions = config.getGroupToPermissionsMap().get(entry.getKey());
                if (permissions != null) {
                    groupPermissions.put(entry.getKey(), permissions);
                }
            }
        }
        return groupPermissions;
    }
}
