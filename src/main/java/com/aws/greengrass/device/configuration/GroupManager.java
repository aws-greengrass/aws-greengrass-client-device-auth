/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration;

import com.aws.greengrass.device.Session;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Singleton manager class for managing device group roles and retrieving permissions associated
 * with Sessions. To determine device permissions, the GroupManager first determines which device
 * groups a Session belongs to, and then merges device group permissions.
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
        Set<String> matchingGroups = findMatchingGroups(config.getDefinitions(), session);
        return matchingGroups.stream()
                .collect(Collectors.toMap(group -> group, group -> config.getGroupToPermissionsMap().get(group)));
    }

    private Set<String> findMatchingGroups(Map<String, GroupDefinition> groupDefinitionMap, Session session) {
        Set<String> matchingGroups = new HashSet<>();

        for (String groupName : groupDefinitionMap.keySet()) {
            GroupDefinition group = groupDefinitionMap.get(groupName);
            if (group.containsClientDevice(session)) {
                matchingGroups.add(groupName);
            }
        }

        return matchingGroups;
    }
}
