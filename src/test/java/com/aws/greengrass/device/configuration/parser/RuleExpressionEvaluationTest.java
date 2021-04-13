/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration.parser;

import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.attribute.StringLiteralAttribute;
import com.aws.greengrass.device.Session;
import com.aws.greengrass.device.configuration.ExpressionVisitor;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class RuleExpressionEvaluationTest {

    ASTStart getTree(String expressionString) throws ParseException {
        RuleExpression parser = new RuleExpression(new StringReader(expressionString));
        return parser.Start();
    }

    Session getSessionWithThing(String thingName) {
        Session session = Mockito.mock(Session.class);
        DeviceAttribute attribute = new StringLiteralAttribute(thingName);
        Mockito.when(session.getSessionAttribute(any(), any())).thenReturn(attribute);
        return session;
    }

    @Test
    public void GIVEN_unaryExpressionWithSessionContainingThing_WHEN_RuleExpressionEvaluated_THEN_EvaluatesTrue() throws ParseException {
        ASTStart tree = getTree("thingName: Thing");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));
    }

    @Test
    public void GIVEN_unaryExpression_WHEN_RuleExpressionEvaluatedWithSessionNotContainingThing_THEN_EvaluatesFalse() throws ParseException {
        ASTStart tree = getTree("thingName: Thing1");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertFalse((Boolean) visitor.visit(tree, session));
    }

    @Test
    public void GIVEN_basicOrExpression_WHEN_RuleExpressionEvaluatedWithSessionContainingOneThing_THEN_EvaluatesTrue() throws ParseException {
        ASTStart tree = getTree("thingName: Thing OR thingName: Thing1");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));
    }

    @Test
    public void GIVEN_basicAndExpression_WHEN_RuleExpressionEvaluatedWithSessionContainingOneThing_THEN_EvaluatesFalse() throws ParseException {
        ASTStart tree = getTree("thingName: Thing AND thingName: Thing1");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertFalse((Boolean) visitor.visit(tree, session));
    }

    @Test
    public void GIVEN_logicalExpressionWithAndOr_WHEN_RuleExpressionEvaluatedWithSessionContainingThingInOR_THEN_EvaluatesTrue() throws ParseException {
        ASTStart tree = getTree("thingName: Thing OR thingName: Thing1 AND thingName: Thing2");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));
    }

    @Test
    public void GIVEN_logicalExpressionWithAndOr_WHEN_RuleExpressionEvaluatedWithSessionContainingThingInAND_THEN_EvaluatesFalse() throws ParseException {
        ASTStart tree = getTree("thingName: Thing AND thingName: Thing1 OR thingName: Thing2");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertFalse((Boolean) visitor.visit(tree, session));
    }
}
