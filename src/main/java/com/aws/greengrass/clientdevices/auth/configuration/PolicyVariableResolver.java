/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.exception.PolicyException;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Pair;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.Map;
import java.util.Set;

public final class PolicyVariableResolver {
    private static final String THING_NAMESPACE = "Thing";
    private static final String THING_NAME_ATTRIBUTE = "ThingName";

    private static final Map<String, Pair<String,String>> policyVariableToAttributeProvider = ImmutableMap.of(
            "${iot:Connection.Thing.ThingName}".toLowerCase(), new Pair<>(THING_NAMESPACE, THING_NAME_ATTRIBUTE)
    );

    private PolicyVariableResolver() {
    }

    /**
     * Utility method to replace policy variables in permissions with device attributes.
     * Policy variables need to be validated when reading the policy document.
     * This method does not handle unsupported policy variables.
     *
     * @param policyVariables list of policy variables in permission format
     * @param format permission format to resolve
     * @param session current device session
     * @return updated format
     * @throws PolicyException when unable to find a policy variable value
     */
    public static String resolvePolicyVariables(Set<String> policyVariables, String format, Session session)
            throws PolicyException {
        if (policyVariables.isEmpty()) {
            return format;
        }
        String substitutedFormat = format;
        for (String policyVariable : policyVariables) {
            String attributeNamespace = policyVariableToAttributeProvider.get(policyVariable.toLowerCase()).getLeft();
            String attributeName = policyVariableToAttributeProvider.get(policyVariable.toLowerCase()).getRight();
            String policyVariableValue = Coerce.toString(session.getSessionAttribute(attributeNamespace,
                    attributeName));
            if (policyVariableValue == null) {
                throw new PolicyException(
                        String.format("No attribute found for policy variable %s in current session", policyVariable));
            } else {
                // StringUtils.replace() is faster than String.replace() since it does not use regex
                substitutedFormat = StringUtils.replace(substitutedFormat, policyVariable, policyVariableValue);
            }
        }
        return substitutedFormat;
    }

    public static boolean isPolicyVariable(String variable) {
        return policyVariableToAttributeProvider.containsKey(variable.toLowerCase());
    }
}
