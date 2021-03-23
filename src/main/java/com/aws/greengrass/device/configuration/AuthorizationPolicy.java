/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Set;

@Value
@Builder
@JsonDeserialize(builder = AuthorizationPolicy.AuthorizationPolicyBuilder.class)
public class AuthorizationPolicy {

    String policyId;

    @Builder.Default
    Effect effect = Effect.ALLOW;

    @Builder.Default
    Set<String> operations = Collections.emptySet();

    @Builder.Default
    Set<String> resources = Collections.emptySet();

    @JsonPOJOBuilder(withPrefix = "")
    public static class AuthorizationPolicyBuilder {
    }

    public enum Effect {
        ALLOW, DENY
    }
}
