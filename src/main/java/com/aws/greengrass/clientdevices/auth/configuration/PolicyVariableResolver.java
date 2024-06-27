/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.exception.PolicyException;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.attribute.Attribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.util.Coerce;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PolicyVariableResolver {

    private static final Function<PolicyVariable, PolicyException> NO_ATTR_FOUND_EXCEPTION = policyVariable ->
            new PolicyException(String.format("No attribute found for policy variable %s in current session",
                    policyVariable));

    private PolicyVariableResolver() {
    }

    /**
     * Utility method to replace policy variables in permissions with device attributes.
     * Policy variables need to be validated when reading the policy document.
     * This method does not handle unsupported policy variables.
     *
     * @param policyVariables list of policy variables in permission format
     * @param format          permission format to resolve
     * @param session         current device session
     * @return updated format
     * @throws PolicyException when unable to find a policy variable value
     */
    public static String resolvePolicyVariables(Set<String> policyVariables, String format, Session session)
            throws PolicyException {
        if (policyVariables.isEmpty()) {
            return format;
        }
        String substitutedFormat = format;
        for (PolicyVariable policyVariable : policyVariables.stream()
                .map(PolicyVariable::parse).map(v -> v.orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList())) {

            DeviceAttribute attr = session.getSessionAttribute(policyVariable.getAttribute());
            if (policyVariable.getAttribute() == Attribute.THING_ATTRIBUTES
                    && !attr.matches(policyVariable.getSelector())) {
                throw NO_ATTR_FOUND_EXCEPTION.apply(policyVariable);
            }

            String policyVariableValue = Coerce.toString(attr);
            if (policyVariableValue == null) {
                throw NO_ATTR_FOUND_EXCEPTION.apply(policyVariable);
            }

            // StringUtils.replace() is faster than String.replace() since it does not use regex
            substitutedFormat = StringUtils.replace(substitutedFormat,
                    policyVariable.getOriginalText(), policyVariableValue);
        }
        return substitutedFormat;
    }

    /**
     * True if the variable is a supported policy variable.
     *
     * @param variable variable
     * @return true if the variable is a support policy variable
     */
    public static boolean isSupportedPolicyVariable(String variable) {
        return PolicyVariable.parse(variable).isPresent();
    }

    /**
     * True if the following variable represents a thing attribute,
     * such as ${iot:Connection.Thing.Attributes[myAttribute]}.
     *
     * @param variable variable
     * @return true if variable is a thing attribute
     */
    public static boolean isAttributePolicyVariable(String variable) {
        return PolicyVariable.parse(variable)
                .filter(var -> var.getAttribute() == Attribute.THING_ATTRIBUTES)
                .isPresent();
    }
}
