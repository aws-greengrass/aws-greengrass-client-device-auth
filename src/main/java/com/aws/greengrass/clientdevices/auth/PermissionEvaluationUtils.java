/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.configuration.Permission;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.SessionConfig;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Utils;
import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final String POLICY_VARIABLE_PATTERN_STRING = "\\$\\{iot:(\\w*+.\\w*+.\\w*+)}";

    private static final String PROVIDER_STRING = "Connection";

    private static final Logger LOGGER = LogManager.getLogger(SessionConfig.class);


    private PermissionEvaluationUtils() {
    }

    /**
     * utility method of authorizing operation to resource.
     *
     * @param operation             operation in the form of 'service:action'
     * @param resource              resource in the form of 'service:resourceType:resourceName'
     * @param groupToPermissionsMap device matching group to permissions map
     * @return whether operation to resource in authorized
     */
    public static boolean isAuthorized(String operation, String resource,
                                       Map<String, Set<Permission>> groupToPermissionsMap) {
        Operation op = parseOperation(operation);
        Resource rsc = parseResource(resource);
        if (!rsc.getService().equals(op.getService())) {
            throw new IllegalArgumentException(
                    String.format("Operation %s service is not same as resource %s service", op, rsc));

        }
        if (groupToPermissionsMap == null || groupToPermissionsMap.isEmpty()) {
            logger.atDebug().kv("operation", operation).kv("resource", resource)
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

    private static boolean comparePrincipal(String requestPrincipal, String policyPrincipal) {
        if (requestPrincipal.equals(policyPrincipal)) {
            return true;
        }

        return ANY_REGEX.equals(policyPrincipal);
    }

    private static boolean compareOperation(Operation requestOperation, String policyOperation) {
        if (requestOperation.toString().equals(policyOperation)) {
            return true;
        }
        if (String.format(SERVICE_OPERATION_FORMAT, requestOperation.getService(), ANY_REGEX).equals(policyOperation)) {
            return true;
        }
        return ANY_REGEX.equals(policyOperation);
    }

    private static boolean compareResource(Resource requestResource, String policyResource) {
        if (requestResource.toString().equals(policyResource)) {
            return true;
        }

        if (String.format(SERVICE_RESOURCE_FORMAT, requestResource.getService(), requestResource.getResourceType(),
                ANY_REGEX).equals(policyResource)) {
            return true;
        }

        return ANY_REGEX.equals(policyResource);
    }

    private static Operation parseOperation(String operationStr) {
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

    private static Resource parseResource(String resourceStr) {
        if (Utils.isEmpty(resourceStr)) {
            throw new IllegalArgumentException("Resource can't be empty");
        }

        Matcher matcher = SERVICE_RESOURCE_PATTERN.matcher(resourceStr);
        if (matcher.matches()) {
            return Resource.builder().service(matcher.group(1)).resourceType(matcher.group(2))
                    .resourceName(matcher.group(3)).build();
        }

        throw new IllegalArgumentException(
                String.format("Resource %s is not in the form of %s", resourceStr, SERVICE_RESOURCE_PATTERN.pattern()));
    }

    /**
     * utility method of updating permission with policy variable value.
     *
     * @param session             current device session
     * @param permission              permission to parse
     * @return updated permission
     */
    public static Permission updateResource(Session session, Permission permission) {

        String resource = permission.getResource();

        Pattern pattern = Pattern.compile(POLICY_VARIABLE_PATTERN_STRING);
        Matcher matcher = pattern.matcher(resource);

        while (matcher.find()) { 

            String[] vars = matcher.group(1).split("\\.");
            String attributeNamespace = vars[1];
            String attributeName = vars[2];

            // this currently supports the ThingName attribute
            if (vars[0].equals(PROVIDER_STRING) && session.containsSessionAttribute(attributeNamespace, attributeName)){

                String policyVariableValue = session.getSessionAttribute(attributeNamespace, attributeName).toString();

                resource = matcher.replaceFirst(policyVariableValue);

                permission = Permission.builder().principal(permission.getPrincipal()).operation(permission.getOperation()).resource(resource).build();
            } else {
                LOGGER.warn("Policy variable {}.{}.{} detected but could not be parsed", vars[0],
                        vars[1], vars[2]);
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
