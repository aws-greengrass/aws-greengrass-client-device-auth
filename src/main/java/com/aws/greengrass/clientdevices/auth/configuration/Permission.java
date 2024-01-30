/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.session.Session;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Permission {
    @NonNull String principal;

    @NonNull String operation;

    @NonNull String resource;

    List<String> policyVariables;

    public String getResource(Session session) {
        return PolicyVariableResolver.resolvePolicyVariables(policyVariables, resource, session);
    }
}
