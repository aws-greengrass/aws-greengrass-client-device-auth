/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.session.attribute.Attribute;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;

@Builder
@Value
public class PolicyVariable {

    private static final String THING_NAME_PATTERN = "${iot:Connection.Thing.ThingName}";
    private static final String THING_NAMESPACE = "Thing";

    private static final String THING_ATTRS_PREFIX = "${iot:Connection.Thing.Attributes[";
    private static final String THING_ATTRS_SUFFIX = "]}";

    String originalText;
    Attribute attribute;
    String selector; // the part within [ ]

    /**
     * Parse a policy variable from string.
     *
     * @param policyVariable variable
     * @return parsed policy variable
     */
    public static Optional<PolicyVariable> parse(@NonNull String policyVariable) {
        // thing name
        if (Objects.equals(policyVariable, THING_NAME_PATTERN)) {
            return Optional.of(PolicyVariable.builder()
                    .originalText(policyVariable)
                    .attribute(Attribute.THING_NAME)
                    .build());
        }

        // thing attributes
        if (policyVariable.startsWith(THING_ATTRS_PREFIX) && policyVariable.endsWith(THING_ATTRS_SUFFIX)) {
            return parseAttributePolicyVariable(policyVariable);
        }

        // unsupported variable
        return Optional.empty();
    }

    private static Optional<PolicyVariable> parseAttributePolicyVariable(@NonNull String policyVariable) {
        int attrStart = THING_ATTRS_PREFIX.length();
        int attrEnd = policyVariable.length() - THING_ATTRS_SUFFIX.length();
        if (attrStart > attrEnd) {
            return Optional.empty();
        }

        String attr = policyVariable.substring(attrStart, attrEnd);
        if (!StringUtils.isAlphanumeric(attr)) {
            return Optional.empty();
        }

        return Optional.of(PolicyVariable.builder()
                .originalText(policyVariable)
                .attribute(Attribute.THING_ATTRIBUTES)
                .selector(attr)
                .build());
    }
}
