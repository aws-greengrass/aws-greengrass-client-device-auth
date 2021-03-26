/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.configuration.Permission;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Utils;
import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PermissionEvaluationUtils {
    private static final Logger logger = LogManager.getLogger(PermissionEvaluationUtils.class);
    private static final String ANY_REGEX = "*";
    private static final String SERVICE_PATTERN_STRING = "([a-z]+)";
    private static final String SERVICE_OPERATION_PATTERN_STRING = "([a-zA-Z0-9-_]+)";
    private static final String SERVICE_RESOURCE_TYPE_PATTERN_STRING = "([a-z]+)";
    private static final String SERVICE_RESOURCE_NAME_PATTERN_STRING = "([a-zA-Z0-9-_$/+]+)";
    private static final String SERVICE_OPERATION_FORMAT = "%s:%s";
    private static final String SERVICE_RESOURCE_FORMAT = "%s:%s:%s";
    private static final Pattern SERVICE_OPERATION_PATTERN = Pattern.compile(
            String.format(SERVICE_OPERATION_FORMAT, SERVICE_PATTERN_STRING, SERVICE_OPERATION_PATTERN_STRING));
    private static final Pattern SERVICE_RESOURCE_PATTERN = Pattern.compile(
            String.format(SERVICE_RESOURCE_FORMAT, SERVICE_PATTERN_STRING, SERVICE_RESOURCE_TYPE_PATTERN_STRING,
                    SERVICE_RESOURCE_NAME_PATTERN_STRING));


    private PermissionEvaluationUtils() {
    }

    /** utility method of authorizing operation to resource.
     * @param operation             operation in the form of 'service:action'
     * @param resource              resource in the form of 'service:resourceType:resourceName'
     * @param groupToPermissionsMap device matching group to permissions map
     * @return whether operation to resource in authorized
     */
    public static boolean isAuthorize(String operation, String resource,
                                      Map<String, Set<Permission>> groupToPermissionsMap) {
        Operation op = parseOperation(operation);
        Optional<Resource> rscOptional = parseResource(resource);
        rscOptional.ifPresent(rsc -> {
            if (!rsc.getService().equals(op.getService())) {
                throw new IllegalArgumentException(
                        String.format("Operation %s service is not same as resource %s " + "service", op, rsc));
            }
        });

        if (groupToPermissionsMap == null || groupToPermissionsMap.isEmpty()) {
            logger.atDebug().log("No authorization group matches, deny the request");
            return false;
        }

        for (Map.Entry<String, Set<Permission>> entry : groupToPermissionsMap.entrySet()) {
            String principal = entry.getKey();
            Set<Permission> permissions = entry.getValue();
            if (Utils.isEmpty(permissions)) {
                continue;
            }

            Permission permission = permissions.stream().filter(e -> {
                if (!comparePrincipal(principal, e.getPrincipal())) {
                    return false;
                }
                if (!compareOperation(op, e.getOperation())) {
                    return false;
                }
                return rscOptional.map(value -> compareResource(value, e.getResource()))
                        .orElseGet(() -> e.getResource() == null);
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

    private static boolean compareOperation(Operation requestOp, String policyOperation) {
        if (requestOp.toString().equals(policyOperation)) {
            return true;
        }
        if (String.format(SERVICE_OPERATION_FORMAT, requestOp.getService(), ANY_REGEX).equals(policyOperation)) {
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
            throw new IllegalArgumentException("operation can't be empty");
        }

        Matcher matcher = SERVICE_OPERATION_PATTERN.matcher(operationStr);
        if (matcher.matches()) {
            return Operation.builder().service(matcher.group(1)).action(matcher.group(2)).build();
        }
        throw new IllegalArgumentException(String.format("Operation %s is not in the form of %s", operationStr,
                SERVICE_OPERATION_PATTERN.pattern()));
    }

    private static Optional<Resource> parseResource(String resourceStr) {
        if (resourceStr == null) {
            return Optional.empty();
        }

        Matcher matcher = SERVICE_RESOURCE_PATTERN.matcher(resourceStr);
        if (matcher.matches()) {
            return Optional.of(Resource.builder().service(matcher.group(1)).resourceType(matcher.group(2))
                    .resourceName(matcher.group(3)).build());
        }

        throw new IllegalArgumentException(
                String.format("Resource %s is not in the form of %s", resourceStr, SERVICE_RESOURCE_PATTERN.pattern()));
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
