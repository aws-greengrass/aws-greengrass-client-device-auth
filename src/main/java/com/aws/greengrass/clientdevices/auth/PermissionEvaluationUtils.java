/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.authorization.WildcardTrie;
import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.configuration.Permission;
import com.aws.greengrass.clientdevices.auth.exception.PolicyException;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Utils;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;

public final class PermissionEvaluationUtils {
    private static final Logger logger = LogManager.getLogger(PermissionEvaluationUtils.class);
    private static final String WILDCARD = "*";
    private static final String DELIM = ":";
    private static final Pattern RESOURCE_NAME_PATTERN =
            Pattern.compile("([\\w -\\/:-@\\[-\\`{-~]+)", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String EXCEPTION_MALFORMED_OPERATION =
            "Operation is malformed, must be of the form: "
            + "([a-zA-Z]+):([a-zA-Z0-9-_]+)";
    private static final String EXCEPTION_MALFORMED_RESOURCE =
            "Resource is malformed, must be of the form: "
            + "([a-zA-Z]+):([a-zA-Z]+):" + RESOURCE_NAME_PATTERN.pattern();
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
        Operation op;
        Resource rsc;
        try {
            op = parseOperation(request.getOperation());
            rsc = parseResource(request.getResource());
        } catch (PolicyException e) {
            logger.atError().setCause(e).log();
            return false;
        }

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
        if (Objects.equals(requestPrincipal, policyPrincipal)) {
            return true;
        }
        return Objects.equals(WILDCARD, policyPrincipal);
    }

    private boolean compareOperation(Operation requestOperation, String policyOperation) {
        if (Objects.equals(requestOperation.getOperationStr(), policyOperation)) {
            return true;
        }
        if (Objects.equals(requestOperation.getService() + DELIM + WILDCARD, policyOperation)) {
            return true;
        }
        return Objects.equals(WILDCARD, policyOperation);
    }

    private boolean compareResource(Resource requestResource, String policyResource) {
        if (Objects.equals(requestResource.getResourceStr(), policyResource)) {
            return true;
        }

        WildcardTrie wildcardTrie = new WildcardTrie();
        wildcardTrie.add(policyResource);
        return wildcardTrie.matchesStandard(requestResource.getResourceStr());
    }

    private Operation parseOperation(String operationStr) throws PolicyException {
        if (Utils.isEmpty(operationStr)) {
            throw new PolicyException("Operation can't be empty");
        }

        int split = operationStr.indexOf(':');
        if (split == -1 || split == operationStr.length() - 1) {
            throw new PolicyException(EXCEPTION_MALFORMED_OPERATION);
        }

        String service = operationStr.substring(0, split);
        if (service.isEmpty() || !StringUtils.isAlpha(service)) {
            throw new PolicyException(EXCEPTION_MALFORMED_OPERATION);
        }

        String action = operationStr.substring(split + 1);
        if (action.isEmpty() || !isAlphanumericWithExtraChars(action, "-_")) {
            throw new PolicyException(EXCEPTION_MALFORMED_OPERATION);
        }

        return Operation.builder()
                .operationStr(operationStr)
                .service(service)
                .action(action)
                .build();
    }

    private Resource parseResource(String resourceStr) throws PolicyException  {
        if (Utils.isEmpty(resourceStr)) {
            throw new PolicyException("Resource can't be empty");
        }

        int split = resourceStr.indexOf(':');
        if (split == -1 || split == resourceStr.length() - 1) {
            throw new PolicyException(EXCEPTION_MALFORMED_RESOURCE);
        }

        String service = resourceStr.substring(0, split);
        if (service.isEmpty() || !StringUtils.isAlpha(service)) {
            throw new PolicyException(EXCEPTION_MALFORMED_RESOURCE);
        }

        String typeAndName = resourceStr.substring(split + 1);
        split = typeAndName.indexOf(':');
        if (split == -1 || split == resourceStr.length() - 1) {
            throw new PolicyException(EXCEPTION_MALFORMED_RESOURCE);
        }

        String resourceType = typeAndName.substring(0, split);
        if (resourceType.isEmpty() || !StringUtils.isAlpha(resourceType)) {
            throw new PolicyException(EXCEPTION_MALFORMED_RESOURCE);
        }

        String resourceName = typeAndName.substring(split + 1); // TODO
        // still using regex because Pattern.UNICODE_CHARACTER_CLASS is complicated
        if (!RESOURCE_NAME_PATTERN.matcher(resourceName).matches()) {
            throw new PolicyException(EXCEPTION_MALFORMED_RESOURCE);
        }

        return Resource.builder()
                .resourceStr(resourceStr)
                .service(service)
                .resourceType(resourceType)
                .resourceName(resourceName)
                .build();
    }

    private static boolean isAlphanumericWithExtraChars(CharSequence cs, String extra) {
        if (Utils.isEmpty(cs)) {
            return false;
        }
        for (int i = 0; i < cs.length(); i++) {
            char curr = cs.charAt(i);
            if (!Character.isLetterOrDigit(curr) && extra.indexOf(curr) == -1) {
                return false;
            }
        }
        return true;
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
