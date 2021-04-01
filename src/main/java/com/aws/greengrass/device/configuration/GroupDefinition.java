/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration;

import com.aws.greengrass.device.Session;
import com.aws.greengrass.device.configuration.parser.ASTStart;
import com.aws.greengrass.device.configuration.parser.ParseException;
import com.aws.greengrass.device.configuration.parser.RuleExpression;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.io.StringReader;

@Value
@JsonDeserialize(builder = GroupDefinition.GroupDefinitionBuilder.class)
public class GroupDefinition {

    ASTStart expressionTree;
    String policyName;


    @Builder
    GroupDefinition(@NonNull String selectionRule, @NonNull String policyName) throws ParseException {
        this.expressionTree = new RuleExpression(new StringReader(selectionRule)).Start();
        this.policyName = policyName;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class GroupDefinitionBuilder {
    }

    public boolean containsSession(Session session) {
        com.aws.greengrass.device.configuration.parser.RuleExpressionVisitor visitor = new RuleExpressionVisitor();
        return (boolean) visitor.visit(expressionTree, session);
    }
}
