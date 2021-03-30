/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration;

import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Value
@JsonDeserialize(builder = GroupConfiguration.GroupConfigurationBuilder.class)
public class GroupConfiguration {
    private static final Logger logger = LogManager.getLogger(GroupConfiguration.class);

    ConfigurationFormatVersion version;

    //group name to group definition map
    Map<String, GroupDefinition> groups;

    //policy name to policy map
    Map<String, Map<String, AuthorizationPolicyStatement>> policies;


    Map<String, Set<Permission>> groupToPermissionsMap;

    @Builder
    GroupConfiguration(ConfigurationFormatVersion version, Map<String, GroupDefinition> groups,
                       Map<String, Map<String, AuthorizationPolicyStatement>> policies) throws AuthorizationException {
        this.version = version == null ? ConfigurationFormatVersion.MAR_05_2021 : version;
        this.groups = groups == null ? Collections.emptyMap() : groups;
        this.policies = policies == null ? Collections.emptyMap() : policies;
        this.groupToPermissionsMap = constructGroupToPermissionsMap();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class GroupConfigurationBuilder {
    }

    private Map<String, Set<Permission>> constructGroupToPermissionsMap() throws AuthorizationException {
        Map<String, Set<Permission>> groupToPermissionsMap = new HashMap<>();

        for (Map.Entry<String, GroupDefinition> groupDefinitionEntry : groups.entrySet()) {
            GroupDefinition groupDefinition = groupDefinitionEntry.getValue();
            if (!policies.containsKey(groupDefinition.getPolicyName())) {
                throw new AuthorizationException(
                        String.format("Policies doesn't have policy named %s", groupDefinition.getPolicyName()));
            }
            groupToPermissionsMap.put(groupDefinitionEntry.getKey(),
                    constructGroupPermission(groupDefinitionEntry.getKey(),
                            policies.get(groupDefinition.getPolicyName())));
        }
        return groupToPermissionsMap;
    }

    private Set<Permission> constructGroupPermission(String groupName,
                                                     Map<String, AuthorizationPolicyStatement> policyStatementMap) {
        Set<Permission> permissions = new HashSet<>();
        for (Map.Entry<String, AuthorizationPolicyStatement> statementEntry : policyStatementMap.entrySet()) {
            AuthorizationPolicyStatement statement = statementEntry.getValue();
            // only accept 'ALLOW' effect for beta launch
            // TODO add 'DENY' effect support
            if (statement.getEffect() == AuthorizationPolicyStatement.Effect.ALLOW) {
                permissions.addAll(convertPolicyStatementToPermission(groupName, statement));
            }
        }
        return permissions;
    }

    private Set<Permission> convertPolicyStatementToPermission(String groupName,
                                                               AuthorizationPolicyStatement statement) {
        Set<Permission> permissions = new HashSet<>();
        for (String operation : statement.getOperations()) {
            if (Utils.isEmpty(operation)) {
                continue;
            }
            for (String resource : statement.getResources()) {
                if (Utils.isEmpty(resource)) {
                    continue;
                }
                permissions
                        .add(Permission.builder().principal(groupName).operation(operation).resource(resource).build());
            }
        }
        return permissions;
    }
}
