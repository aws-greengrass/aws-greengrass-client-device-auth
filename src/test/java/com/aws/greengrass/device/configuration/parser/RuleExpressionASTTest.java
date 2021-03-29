/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;

@ExtendWith({MockitoExtension.class})
public class RuleExpressionASTTest {

    ASTStart getTree(String expressionString) throws ParseException {
        RuleExpression parser = new RuleExpression(new StringReader(expressionString));
        return parser.Start();
    }

    @Test
    public void GIVEN_unaryExpression_WHEN_RuleExpressionStart_THEN_treeContainsSingleThingNode() throws ParseException {
        ASTStart tree = getTree("ThingName: Thing1");
        Assertions.assertEquals(1, tree.children.length);
        Node thingNode = tree.children[0];
        Assertions.assertEquals(ASTThing.class, thingNode.getClass());
        tree.dump("");
        //Assertions.assertEquals("Thing1", thingNode.jjtGetValue());
    }

    @Test
    public void GIVEN_logicalORExpression_WHEN_RuleExpressionStart_THEN_treeContainsLogicalORExpression() throws ParseException {
        ASTStart tree = getTree("ThingName: Thing1 OR ThingName: Thing2");
        Assertions.assertEquals(1, tree.children.length);
        Node orNode = tree.children[0];
        Assertions.assertEquals(ASTOr.class, orNode.getClass());
        tree.dump("");
    }
}
