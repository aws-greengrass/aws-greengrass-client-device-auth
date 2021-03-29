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
        ASTThing thingNode = (ASTThing) tree.children[0];
        Assertions.assertEquals(ASTThing.class, thingNode.getClass());
        Assertions.assertEquals("Thing1", thingNode.jjtGetValue());
    }

    @Test
    public void GIVEN_logicalORExpression_WHEN_RuleExpressionStart_THEN_treeContainsOrNode() throws ParseException {
        ASTStart tree = getTree("ThingName: Thing1 OR ThingName: Thing2");
        Assertions.assertEquals(1, tree.children.length);
        Node orNode = tree.children[0];
        Assertions.assertEquals(ASTOr.class, orNode.getClass());
        // TODO: There has to be a better way of doing this
        Assertions.assertEquals(ASTThing.class, orNode.jjtGetChild(0).getClass());
        Assertions.assertEquals(ASTThing.class, orNode.jjtGetChild(1).getClass());
    }

    @Test
    public void GIVEN_logicalANDExpression_WHEN_RuleExpressionStart_THEN_treeContainsAndNode() throws ParseException {
        ASTStart tree = getTree("ThingName: Thing1 AND ThingName: Thing2");
        Assertions.assertEquals(1, tree.children.length);
        Node andNode = tree.children[0];
        Assertions.assertEquals(ASTAnd.class, andNode.getClass());
        Assertions.assertEquals(ASTThing.class, andNode.jjtGetChild(0).getClass());
        Assertions.assertEquals(ASTThing.class, andNode.jjtGetChild(1).getClass());
    }

    @Test
    public void GIVEN_expressionWithAndOr_WHEN_RuleExpressionStart_THEN_andOperationEvaluatesFirst() throws ParseException {
        ASTStart tree = getTree("ThingName: Thing1 AND ThingName: Thing2 OR ThingName: Thing3");
        Assertions.assertEquals(1, tree.children.length);
        ASTOr orNode = (ASTOr) tree.children[0];
        Assertions.assertEquals(ASTAnd.class, orNode.jjtGetChild(0).getClass());
        Assertions.assertEquals(ASTThing.class, orNode.jjtGetChild(0).jjtGetChild(0).getClass());
        Assertions.assertEquals(ASTThing.class, orNode.jjtGetChild(0).jjtGetChild(1).getClass());
        Assertions.assertEquals(ASTThing.class, orNode.jjtGetChild(1).getClass());
    }
}
