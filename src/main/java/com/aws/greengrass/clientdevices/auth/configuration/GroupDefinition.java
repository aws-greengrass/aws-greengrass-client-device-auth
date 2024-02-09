/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.configuration.parser.ASTStart;
import com.aws.greengrass.clientdevices.auth.configuration.parser.ParseException;
import com.aws.greengrass.clientdevices.auth.configuration.parser.RuleExpression;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.io.StringReader;

@Value
@JsonDeserialize(builder = GroupDefinition.GroupDefinitionBuilder.class)
public class GroupDefinition {

    @JsonIgnore
    ASTStart expressionTree;
    String selectionRule;
    String policyName;


    @Builder
    GroupDefinition(@NonNull String selectionRule, @NonNull String policyName) throws ParseException {
        this.selectionRule = selectionRule;
        this.expressionTree = new RuleExpression(new StringReader(selectionRule)).Start();
        this.policyName = policyName;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class GroupDefinitionBuilder {
    }

    /**
     * Returns true if the client device represented by the specified session belongs to this device group.
     *
     * @param session session representing the client device to be tested
     * @return true if the client device belongs to the group
     */
    public boolean containsClientDevice(Session session) {
        ExpressionVisitor visitor = new ExpressionVisitor();
        return (boolean) visitor.visit(expressionTree, session);
    }
}
