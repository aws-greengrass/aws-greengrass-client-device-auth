/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.exception.PolicyException;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Value
@JsonDeserialize(builder = GroupConfiguration.GroupConfigurationBuilder.class)
public class GroupConfiguration {
    private static final Logger logger = LogManager.getLogger(GroupConfiguration.class);

    ConfigurationFormatVersion formatVersion;
    Map<String, GroupDefinition> definitions;
    Map<String, Map<String, AuthorizationPolicyStatement>> policies;
    Map<String, Set<Permission>> groupToPermissionsMap;
    boolean hasDeviceAttributeVariables;

    @Builder
    GroupConfiguration(ConfigurationFormatVersion formatVersion, Map<String, GroupDefinition> definitions,
                       Map<String, Map<String, AuthorizationPolicyStatement>> policies) {
        this.formatVersion = formatVersion == null ? ConfigurationFormatVersion.MAR_05_2021 : formatVersion;

        GroupPermissionConstructor constructor = new GroupPermissionConstructor(definitions, policies);
        this.definitions = constructor.getDefinitions();
        this.policies = constructor.getPolicies();
        this.groupToPermissionsMap = constructor.getPermissions();
        this.hasDeviceAttributeVariables = constructor.isHasDeviceAttributeVariables();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class GroupConfigurationBuilder {
    }

    @Getter
    private static class GroupPermissionConstructor {

        private static final Pattern POLICY_VARIABLE_PATTERN = Pattern.compile("\\$\\{.*?}");

        private final Map<String, GroupDefinition> definitions;
        private final Map<String, Map<String, AuthorizationPolicyStatement>> policies;
        private final Map<String, Set<Permission>> permissions;
        private boolean hasDeviceAttributeVariables;

        GroupPermissionConstructor(Map<String, GroupDefinition> definitions,
                                   Map<String, Map<String, AuthorizationPolicyStatement>> policies) {
            this.definitions = definitions == null ? Collections.emptyMap() : definitions;
            this.policies = policies == null ? Collections.emptyMap() : policies;
            this.permissions = constructGroupPermissions();
        }

        private Map<String, Set<Permission>> constructGroupPermissions() {
            return definitions.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> constructGroupPermission(
                            entry.getKey(),
                            policies.getOrDefault(entry.getValue().getPolicyName(),
                                    Collections.emptyMap()))));
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
                    permissions.add(
                            Permission.builder().principal(groupName).operation(operation).resource(resource)
                                    .resourcePolicyVariables(findPolicyVariables(resource)).build());
                }
            }
            return permissions;
        }

        private Set<String> findPolicyVariables(String resource) {
            Matcher matcher = POLICY_VARIABLE_PATTERN.matcher(resource);
            Set<String> policyVariables = new HashSet<>();
            while (matcher.find()) {
                String policyVariable = matcher.group(0);
                if (PolicyVariableResolver.isAttributePolicyVariable(policyVariable)) {
                    hasDeviceAttributeVariables = true;
                }
                policyVariables.add(policyVariable);
            }
            return policyVariables;
        }
    }

    /**
     * Validate the deviceGroups configuration.
     *
     * @throws PolicyException if an invalid policy is detected
     */
    public void validate() throws PolicyException {
        String missingPolicy = definitions.values().stream()
                .map(GroupDefinition::getPolicyName)
                .filter(policyName -> !policies.containsKey(policyName))
                .findFirst()
                .orElse(null);
        if (missingPolicy != null) {
            throw new PolicyException(
                    String.format("Policy definition %s does not have a corresponding policy", missingPolicy));
        }

        if (!groupToPermissionsMap.values().stream()
                .flatMap(permissions -> permissions.stream().flatMap(p -> p.getResourcePolicyVariables().stream()))
                .allMatch(PolicyVariableResolver::isSupportedPolicyVariable)) {
            throw new PolicyException("Policy contains unknown variables");
        }
    }
}
