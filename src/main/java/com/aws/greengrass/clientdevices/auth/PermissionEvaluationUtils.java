/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.configuration.Permission;
import com.aws.greengrass.clientdevices.auth.exception.PolicyException;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Utils;
import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
    public boolean isAuthorized(AuthorizationRequest request, Session session) {
        Operation op = parseOperation(request.getOperation());
        Resource rsc = parseResource(request.getResource());

        if (!rsc.getService().equals(op.getService())) {
            throw new IllegalArgumentException(
                    String.format("Operation %s service is not same as resource %s service", op, rsc));
        }

        Map<String, Set<Permission>> groupPermissions = groupManager.getApplicablePolicyPermissions(session);

        if (groupPermissions == null || groupPermissions.isEmpty()) {
            logger.atDebug().kv("operation", request.getOperation()).kv("resource", request.getResource())
                    .log("No authorization group matches, " + "deny the request");
            return false;
        }

        for (Map.Entry<String, Set<Permission>> entry : groupPermissions.entrySet()) {
            String principal = entry.getKey();

            // Find the first matching permission since we don't support 'deny' operation yet.
            //TODO add support of 'deny' operation
            for (Permission permission : entry.getValue()) {
                if (!comparePrincipal(principal, permission.getPrincipal())) {
                    continue;
                }
                if (!compareOperation(op, permission.getOperation())) {
                    continue;
                }

                String resource;
                try {
                    resource = permission.getResource(session);
                } catch (PolicyException er) {
                    logger.atError().setCause(er).log();
                    continue;
                }

                if (!compareResource(rsc, resource)) {
                    continue;
                }

                logger.atDebug().log("Hit policy with permission {}", permission);
                return true;
            }
        }
        return false;
    }

    private boolean comparePrincipal(String requestPrincipal, String policyPrincipal) {
        if (requestPrincipal.equals(policyPrincipal)) {
            return true;
        }

        return ANY_REGEX.equals(policyPrincipal);
    }

    private boolean compareOperation(Operation requestOperation, String policyOperation) {
        if (requestOperation.getOperationStr().equals(policyOperation)) {
            return true;
        }
        if (String.format(SERVICE_OPERATION_FORMAT, requestOperation.getService(), ANY_REGEX).equals(policyOperation)) {
            return true;
        }
        return ANY_REGEX.equals(policyOperation);
    }

    private boolean compareResource(Resource requestResource, String policyResource) {
        if (requestResource.getResourceStr().equals(policyResource)) {
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
            return Operation.builder().operationStr(operationStr).service(matcher.group(1)).action(matcher.group(2))
                    .build();
        }
        throw new IllegalArgumentException(String.format("Operation %s is not in the form of %s", operationStr,
                SERVICE_OPERATION_PATTERN.pattern()));
    }

    private Resource parseResource(String resourceStr) {
        if (Utils.isEmpty(resourceStr)) {
            throw new IllegalArgumentException("Resource can't be empty");
        }

        Matcher matcher = SERVICE_RESOURCE_PATTERN.matcher(resourceStr);
        if (matcher.matches()) { // TODO
            return Resource.builder().resourceStr(resourceStr)
                    .service(matcher.group(1))
                    .resourceType(matcher.group(2))
                    .resourceName(matcher.group(3))
                    .build();
        }

        throw new IllegalArgumentException(
                String.format("Resource %s is not in the form of %s", resourceStr, SERVICE_RESOURCE_PATTERN.pattern()));
    }

    @Value
    @Builder
    private static class Operation {
        String operationStr;
        String service;
        String action;
    }

    @Value
    @Builder
    private static class Resource {
        String resourceStr;
        String service;
        String resourceType;
        String resourceName;
    }
}
