/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.Pair;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PolicyVariableResolver {
    private static final Map<String, Pair<String,String>> policyVariableMap;
    private static final String THING_NAMESPACE = "Thing";
    private static final String THING_NAME_ATTRIBUTE = "ThingName";

    static {
        Map<String, Pair<String,String>> map = new HashMap<>();

        map.put("${iot:Connection.Thing.ThingName}",
                new Pair<>(THING_NAMESPACE, THING_NAME_ATTRIBUTE));

        policyVariableMap = Collections.unmodifiableMap(map);
    }

    private PolicyVariableResolver() {
    }

    /**
     * Utility method to replace policy variables in the resource with device attributes.
     *
     * @param policyVariables list of policy variables in resource
     * @param resource resource to resolve
     * @param session current device session
     * @return updated resource
     */
    public static String resolvePolicyVariables(List<String> policyVariables, String resource, Session session) {
        if (policyVariables == null || policyVariables.isEmpty()) {
            return resource;
        }
        String substitutedResource = resource;
        for (String policyVariable : policyVariables) {
            String attributeNamespace = policyVariableMap.get(policyVariable).getLeft();
            String attributeName = policyVariableMap.get(policyVariable).getRight();
            String policyVariableValue = Coerce.toString(session.getSessionAttribute(attributeNamespace,
                    attributeName));
            if (policyVariableValue == null) {
                throw new IllegalArgumentException("No attribute found for current session");
            } else {
                substitutedResource = StringUtils.replace(substitutedResource, policyVariable, policyVariableValue);
            }
        }
        return substitutedResource;
    }
}
