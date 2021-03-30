/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@JsonDeserialize(builder = GroupDefinition.GroupDefinitionBuilder.class)
public class GroupDefinition {

    String selectionRule;

    // RuleExpressionNode ruleExpressionTree;

    String policyName;

    @Builder
    GroupDefinition(@NonNull String selectionRule, @NonNull String policyName) {
        this.selectionRule = selectionRule;
        //TODO build binary expression tree from rule string
        // this.ruleExpressionTree = null;
        this.policyName = policyName;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class GroupDefinitionBuilder {
    }
}
