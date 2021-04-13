/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration.parser;

import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringReader;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class BasicRuleExpressionTest {

    void expectValidExpression(String expressionString) throws ParseException {
        RuleExpression parser = new RuleExpression(new StringReader(expressionString));
        parser.expression();
    }

    void expectParseException(String expressionString) {
        RuleExpression parser = new RuleExpression(new StringReader(expressionString));
        Assertions.assertThrows(ParseException.class, () -> parser.expression());
    }

    void expectTokenMgrError(String expressionString) throws TokenMgrError {
        RuleExpression parser = new RuleExpression(new StringReader(expressionString));
        Assertions.assertThrows(TokenMgrError.class, () -> parser.expression());
    }

    @Test
    public void GIVEN_lowerCaseThingName_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: lowercase");
    }

    @Test
    public void GIVEN_upperCaseThingName_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: UPPERCASE");
    }

    @Test
    public void GIVEN_upperAndLowerCaseThingName_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: lowerUPPER");
    }

    @Test
    public void GIVEN_thingNameWithTrailingDigits_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: thing1");
    }

    @Test
    public void GIVEN_thingNameWithDashAndDigit_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: thing-1");
    }

    @Test
    public void GIVEN_thingNameWithUnderscoreAndDigit_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: thing_1");
    }

    @Test
    public void GIVEN_thingNameWithLeadingDigit_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: 1thing");
    }

    @Test
    public void GIVEN_thingNameWithLeadingDash_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: -thing");
    }

    @Test
    public void GIVEN_thingNameWithLeadingUnderscore_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: _thing");
    }

    @Test
    public void GIVEN_thingNameWithEscapedColon_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: thi\\:ng");
    }

    @Test
    public void GIVEN_thingNameWithAllCharacters_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_\\:");
    }

    @Test
    public void GIVEN_thingNameWithNoSpace_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName:thing");
    }

    @Test
    public void GIVEN_basicLogicalORExpression_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: Thing1 OR thingName: Thing2");
    }

    @Test
    public void GIVEN_basicLogicalANDExpression_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: Thing1 AND thingName: Thing2");
    }

    @Test
    public void GIVEN_logicalExpressionWithAndOr_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: Thing1 AND thingName: Thing2 OR thingName: Thing3");
    }

    @Test
    public void GIVEN_expressionWithoutThingName_WHEN_RuleExpression_THEN_exceptionIsThrown() {
        expectParseException("thingName:");
    }

    @Test
    public void GIVEN_expressionWithoutSeparatingColon_WHEN_RuleExpression_THEN_exceptionIsThrown() {
        expectParseException("ThingName thing");
    }

    @Test
    public void GIVEN_thingNameWithUnescapedColon_WHEN_RuleExpression_THEN_exceptionIsThrown() {
        expectTokenMgrError("thingName: :");
    }
}
