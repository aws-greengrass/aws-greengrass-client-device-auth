/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration.parser;

import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.attribute.WildcardSuffixAttribute;
import com.aws.greengrass.clientdevices.auth.configuration.ExpressionVisitor;
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
        DeviceAttribute attribute = new WildcardSuffixAttribute(thingName);
        Mockito.when(session.getSessionAttribute(any(), any())).thenReturn(attribute);
        return session;
    }

    @Test
    void GIVEN_unaryExpressionWithSessionContainingThing_WHEN_RuleExpressionEvaluated_THEN_EvaluatesTrue() throws ParseException {
        ASTStart tree = getTree("thingName: Thing");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_unaryExpression_WHEN_RuleExpressionEvaluatedWithSessionNotContainingThing_THEN_EvaluatesFalse() throws ParseException {
        ASTStart tree = getTree("thingName: Thing1");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertFalse((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_basicOrExpression_WHEN_RuleExpressionEvaluatedWithSessionContainingOneThing_THEN_EvaluatesTrue() throws ParseException {
        ASTStart tree = getTree("thingName: Thing OR thingName: Thing1");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_basicAndExpression_WHEN_RuleExpressionEvaluatedWithSessionContainingOneThing_THEN_EvaluatesFalse() throws ParseException {
        ASTStart tree = getTree("thingName: Thing AND thingName: Thing1");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertFalse((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_logicalExpressionWithAndOr_WHEN_RuleExpressionEvaluatedWithSessionContainingThingInOR_THEN_EvaluatesTrue() throws ParseException {
        ASTStart tree = getTree("thingName: Thing OR thingName: Thing1 AND thingName: Thing2");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_logicalExpressionWithAndOr_WHEN_RuleExpressionEvaluatedWithSessionContainingThingInAND_THEN_EvaluatesFalse() throws ParseException {
        ASTStart tree = getTree("thingName: Thing AND thingName: Thing1 OR thingName: Thing2");
        Session session = getSessionWithThing("Thing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertFalse((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_unaryExpressionWithTrailingWildcard_WHEN_RuleExpressionEvaluated_THEN_EvaluatesTrue() throws ParseException {
        ASTStart tree = getTree("thingName: Thing*");
        Session session = getSessionWithThing("Thing1");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));

        session = getSessionWithThing("ThingTwo");
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_unaryExpressionWithLeadingWildcard_WHEN_RuleExpressionEvaluated_THEN_EvaluatesTrue() throws ParseException {
        ASTStart tree = getTree("thingName: *Thing");
        Session session = getSessionWithThing("FirstThing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));

        session = getSessionWithThing("SecondThing");
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_unaryExpressionWithWildcardThingName_WHEN_RuleExpressionEvaluated_THEN_EvaluatesTrue() throws ParseException {
        ASTStart tree = getTree("thingName: *");
        Session session = getSessionWithThing("Thing1");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));

        session = getSessionWithThing("ThingTwo");
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_unaryExpressionWithTrailingWildcard_WHEN_RuleExpressionEvaluatedWithSessionNotContainingThing_THEN_EvaluatesFalse() throws ParseException {
        ASTStart tree = getTree("thingName: Thing*");
        Session session = getSessionWithThing("FirstThing");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertFalse((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_unaryExpressionWithLeadingWildcard_WHEN_RuleExpressionEvaluatedWithSessionNotContainingThing_THEN_EvaluatesFalse() throws ParseException {
        ASTStart tree = getTree("thingName: *Thing");
        Session session = getSessionWithThing("ThingExample");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertFalse((Boolean) visitor.visit(tree, session));
    }
    @Test
    void GIVEN_unaryExpressionWithMultipleWildcards_WHEN_RuleExpressionEvaluatedWithSessionContainingThing_THEN_EvaluatesFalse() throws ParseException {
        ASTStart tree = getTree("thingName: *Thing*");
        Session session = getSessionWithThing("FirstThingExample");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));

        session = getSessionWithThing("FirstThing");
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));

        session = getSessionWithThing("ThingTwo");
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));

        session = getSessionWithThing("FirstOrSecondThingTwo");
        Assertions.assertTrue((Boolean) visitor.visit(tree, session));
    }

    @Test
    void GIVEN_unaryExpressionWithMultipleWildcards_WHEN_RuleExpressionEvaluatedWithSessionNotContainingThing_THEN_EvaluatesFalse() throws ParseException {
        ASTStart tree = getTree("thingName: *Thing*");
        Session session = getSessionWithThing("FirstExample");
        RuleExpressionVisitor visitor = new ExpressionVisitor();
        Assertions.assertFalse((Boolean) visitor.visit(tree, session));

        session = getSessionWithThing("FirstThBreakingThwo");
        Assertions.assertFalse((Boolean) visitor.visit(tree, session));
    }
}
