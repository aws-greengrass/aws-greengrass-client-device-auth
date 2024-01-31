/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.exception.AttributeProviderException;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.util.Pair;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.List;
import java.util.Map;

public final class PolicyVariableResolver {
    private static final String THING_NAMESPACE = "Thing";
    private static final String THING_NAME_ATTRIBUTE = "ThingName";

    private static final Map<String, Pair<String,String>> policyVariableToAttributeProvider = ImmutableMap.of(
            "${iot:Connection.Thing.ThingName}", new Pair<>(THING_NAMESPACE, THING_NAME_ATTRIBUTE)
    );

    private PolicyVariableResolver() {
    }

    /**
     * Utility method to replace policy variables in the resource with device attributes.
     *
     * @param policyVariables list of policy variables in permission format
     * @param format permission format to resolve
     * @param session current device session
     * @return updated resource
     * @throws AttributeProviderException when unable to find a policy variable value
     */
    public static String resolvePolicyVariables(List<String> policyVariables, String format, Session session)
            throws AttributeProviderException {
        if (policyVariables.isEmpty()) {
            return format;
        }
        String substitutedFormat = format;
        for (String policyVariable : policyVariables) {
            String attributeNamespace = policyVariableToAttributeProvider.get(policyVariable).getLeft();
            String attributeName = policyVariableToAttributeProvider.get(policyVariable).getRight();
            String policyVariableValue = session.getSessionAttribute(attributeNamespace, attributeName).toString();
            if (policyVariableValue == null) {
                throw new AttributeProviderException(
                        String.format("No attribute found for policy variable %s in current session", policyVariable));
            } else {
                // StringUtils.replace() is faster than String.replace() since it does not use regex
                substitutedFormat = StringUtils.replace(substitutedFormat, policyVariable, policyVariableValue);
            }
        }
        return substitutedFormat;
    }
}
