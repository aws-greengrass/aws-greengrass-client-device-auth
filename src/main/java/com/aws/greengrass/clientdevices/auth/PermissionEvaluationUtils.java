/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.configuration.Permission;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Utils;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;

public final class PermissionEvaluationUtils {
    private static final Logger logger = LogManager.getLogger(PermissionEvaluationUtils.class);
    private static final String ANY_REGEX = "*";
    private static final String SERVICE_PATTERN_STRING = "([a-zA-Z]+)";
    private static final String SERVICE_OPERATION_PATTERN_STRING = "([a-zA-Z0-9-_]+)";
    private static final String SERVICE_RESOURCE_TYPE_PATTERN_STRING = "([a-zA-Z]+)";
    // Characters, digits, special chars, space (Allowed in MQTT topics)
    private static final String SERVICE_RESOURCE_NAME_PATTERN_STRING = "([\\w -\\/:-@\\[-\\`{-~]+)";
    private static final String SERVICE_OPERATION_FORMAT = "%s:%s";
    private static final String SERVICE_RESOURCE_FORMAT = "%s:%s:%s";
    private static final Pattern SERVICE_OPERATION_PATTERN = Pattern.compile(
            String.format(SERVICE_OPERATION_FORMAT, SERVICE_PATTERN_STRING, SERVICE_OPERATION_PATTERN_STRING));
    private static final Pattern SERVICE_RESOURCE_PATTERN = Pattern.compile(
            String.format(SERVICE_RESOURCE_FORMAT, SERVICE_PATTERN_STRING, SERVICE_RESOURCE_TYPE_PATTERN_STRING,
                    SERVICE_RESOURCE_NAME_PATTERN_STRING), Pattern.UNICODE_CHARACTER_CLASS);

    private static final String POLICY_VARIABLE_FORMAT = "\\$\\{iot:(Connection.Thing.ThingName)}";

    private static final Pattern POLICY_VARIABLE_PATTERN = Pattern.compile(POLICY_VARIABLE_FORMAT,
            Pattern.CASE_INSENSITIVE);

    private static final String THING_NAME_VARIABLE = "Connection.Thing.ThingName";
    private final GroupManager groupManager;

    /**
     * Constructor for PermissionEvaluationUtils.
     *
     * @param groupManager  Group Manager
     */
    @Inject
    public PermissionEvaluationUtils(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    /**
     * utility method of authorizing operation to resource.
     *
     * @param request   Authorization Request
     * @param session   Session
     *
     * @return boolean indicating if the operation requested is authorized
     */
    public boolean isAuthorized(AuthorizationRequest request, Session session) {
        Operation op = parseOperation(request.getOperation());
        Resource rsc = parseResource(request.getResource());

        if (!rsc.getService().equals(op.getService())) {
            throw new IllegalArgumentException(
                    String.format("Operation %s service is not same as resource %s service", op, rsc));

        }

        Map<String, Set<Permission>> groupToPermissionsMap;
        try {
            groupToPermissionsMap = transformGroupPermissionsWithVariableValue(session,
                    groupManager.getApplicablePolicyPermissions(session));
        } catch (IllegalArgumentException e) {
            logger.atError().setCause(e).log(e.getMessage());
            return false;
        }

        if (groupToPermissionsMap == null || groupToPermissionsMap.isEmpty()) {
            logger.atDebug().kv("operation", request.getOperation()).kv("resource", request.getResource())
                    .log("No authorization group matches, " + "deny the request");
            return false;
        }

        for (Map.Entry<String, Set<Permission>> entry : groupToPermissionsMap.entrySet()) {
            String principal = entry.getKey();
            Set<Permission> permissions = entry.getValue();
            if (Utils.isEmpty(permissions)) {
                continue;
            }

            // Find the first matching permission since we don't support 'deny' operation yet.
            //TODO add support of 'deny' operation
            Permission permission = permissions.stream().filter(e -> {
                if (!comparePrincipal(principal, e.getPrincipal())) {
                    return false;
                }
                if (!compareOperation(op, e.getOperation())) {
                    return false;
                }
                return compareResource(rsc, e.getResource());
            }).findFirst().orElse(null);

            if (permission != null) {
                logger.atDebug().log("Hit policy with permission {}", permission);
                return true;
            }
        }

        return false;
    }

    /**
     * utility method to transform map of group permissions by updating policy variables with device variable values.
     *
     * @param session    current device session
     * @param groupToPermissionsMap set of permissions for each device group
     * @return permission map with updated resources
     */
    public Map<String, Set<Permission>> transformGroupPermissionsWithVariableValue(
            @NonNull Session session, Map<String, Set<Permission>> groupToPermissionsMap) {
        return groupToPermissionsMap.entrySet().stream()
                .filter(entry -> !Utils.isEmpty(entry.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(permission -> replaceResourcePolicyVariable(session, permission))
                                .collect(Collectors.toSet())
                ));
    }

    private boolean comparePrincipal(String requestPrincipal, String policyPrincipal) {
        if (requestPrincipal.equals(policyPrincipal)) {
            return true;
        }

        return ANY_REGEX.equals(policyPrincipal);
    }

    private boolean compareOperation(Operation requestOperation, String policyOperation) {
        if (requestOperation.toString().equals(policyOperation)) {
            return true;
        }
        if (String.format(SERVICE_OPERATION_FORMAT, requestOperation.getService(), ANY_REGEX).equals(policyOperation)) {
            return true;
        }
        return ANY_REGEX.equals(policyOperation);
    }

    private boolean compareResource(Resource requestResource, String policyResource) {
        if (requestResource.toString().equals(policyResource)) {
            return true;
        }

        if (String.format(SERVICE_RESOURCE_FORMAT, requestResource.getService(), requestResource.getResourceType(),
                ANY_REGEX).equals(policyResource)) {
            return true;
        }

        return ANY_REGEX.equals(policyResource);
    }

    private Operation parseOperation(String operationStr) {
        if (Utils.isEmpty(operationStr)) {
            throw new IllegalArgumentException("Operation can't be empty");
        }

        Matcher matcher = SERVICE_OPERATION_PATTERN.matcher(operationStr);
        if (matcher.matches()) {
            return Operation.builder().service(matcher.group(1)).action(matcher.group(2)).build();
        }
        throw new IllegalArgumentException(String.format("Operation %s is not in the form of %s", operationStr,
                SERVICE_OPERATION_PATTERN.pattern()));
    }

    private Resource parseResource(String resourceStr) {
        if (Utils.isEmpty(resourceStr)) {
            throw new IllegalArgumentException("Resource can't be empty");
        }

        Matcher matcher = SERVICE_RESOURCE_PATTERN.matcher(resourceStr);
        if (matcher.matches()) {
            return Resource.builder().service(matcher.group(1))
                    .resourceType(matcher.group(2))
                    .resourceName(matcher.group(3))
                    .build();
        }

        throw new IllegalArgumentException(
                String.format("Resource %s is not in the form of %s", resourceStr, SERVICE_RESOURCE_PATTERN.pattern()));
    }

    /**
     * utility method to replace policy variables in permission with device attribute.
     *
     * @param session    current device session
     * @param permission permission to parse
     * @return updated permission
     */
    Permission replaceResourcePolicyVariable(@NonNull Session session, @NonNull Permission permission) {

        String resource = permission.getResource();

        Matcher matcher = POLICY_VARIABLE_PATTERN.matcher(resource);

        while (matcher.find()) {
            String policyVariable = matcher.group(1);
            String[] vars = policyVariable.split("\\.");
            if (vars.length < 3) {
                throw new IllegalArgumentException("Policy variable does not contain attribute information");
            }
            String attributeNamespace = vars[1];
            String attributeName = vars[2];

            // this supports the ThingName attribute only
            if (THING_NAME_VARIABLE.equalsIgnoreCase(policyVariable)) {
                String policyVariableValue =
                        Coerce.toString(session.getSessionAttribute(attributeNamespace, attributeName));

                if (policyVariableValue == null) {
                    throw new IllegalArgumentException("No attribute found for current session");
                } else {
                    // for ThingName support only, we can use .replaceAll()
                    // to support additional policy variables in the future
                    resource = matcher.replaceFirst(policyVariableValue);
                    matcher = POLICY_VARIABLE_PATTERN.matcher(resource);
                    permission = Permission.builder()
                            .principal(permission.getPrincipal())
                            .operation(permission.getOperation())
                            .resource(resource).build();
                }
            } else {
                logger.atWarn().kv("policyVariable", policyVariable)
                        .log("Policy variable unsupported. Only thing name variables are supported, please fix the "
                                + "config");
            }
        }
        return permission;
    }

    @Value
    @Builder
    private static class Operation {
        String service;
        String action;

        @Override
        public String toString() {
            return String.format(SERVICE_OPERATION_FORMAT, service, action);
        }
    }

    @Value
    @Builder
    private static class Resource {
        String service;
        String resourceType;
        String resourceName;

        @Override
        public String toString() {
            return String.format(SERVICE_RESOURCE_FORMAT, service, resourceType, resourceName);
        }
    }
}
