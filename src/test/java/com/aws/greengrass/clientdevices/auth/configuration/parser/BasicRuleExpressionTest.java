/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration.parser;

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
        parser.Start();
    }

    void expectParseException(String expressionString) {
        RuleExpression parser = new RuleExpression(new StringReader(expressionString));
        Assertions.assertThrows(ParseException.class, () -> parser.Start());
    }

    void expectTokenMgrError(String expressionString) throws TokenMgrError {
        RuleExpression parser = new RuleExpression(new StringReader(expressionString));
        Assertions.assertThrows(TokenMgrError.class, () -> parser.Start());
    }

    @Test
    void GIVEN_lowerCaseThingName_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: lowercase");
    }

    @Test
    void GIVEN_upperCaseThingName_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: UPPERCASE");
    }

    @Test
    void GIVEN_upperAndLowerCaseThingName_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: lowerUPPER");
    }

    @Test
    void GIVEN_thingNameWithTrailingDigits_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: thing1");
    }

    @Test
    void GIVEN_thingNameWithDashAndDigit_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: thing-1");
    }

    @Test
    void GIVEN_thingNameWithUnderscoreAndDigit_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: thing_1");
    }

    @Test
    void GIVEN_thingNameWithLeadingDigit_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: 1thing");
    }

    @Test
    void GIVEN_thingNameWithLeadingDash_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: -thing");
    }

    @Test
    void GIVEN_thingNameWithLeadingUnderscore_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: _thing");
    }

    @Test
    void GIVEN_thingNameWithEscapedColon_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: thi\\:ng");
    }

    @Test
    void GIVEN_thingNameWithAllCharacters_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_\\:");
    }

    @Test
    void GIVEN_thingNameWithNoSpace_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName:thing");
    }

    @Test
    void GIVEN_wildcardAsThingName_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: *");
    }

    @Test
    void GIVEN_basicLogicalORExpression_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: Thing1 OR thingName: Thing2");
    }

    @Test
    void GIVEN_basicLogicalANDExpression_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: Thing1 AND thingName: Thing2");
    }

    @Test
    void GIVEN_logicalExpressionWithAndOr_WHEN_RuleExpression_THEN_ruleIsParsed() throws ParseException {
        expectValidExpression("thingName: Thing1 AND thingName: Thing2 OR thingName: Thing3");
    }

    @Test
    void GIVEN_expressionWithoutThingName_WHEN_RuleExpression_THEN_exceptionIsThrown() {
        expectParseException("thingName:");
    }

    @Test
    void GIVEN_expressionWithoutSeparatingColon_WHEN_RuleExpression_THEN_exceptionIsThrown() {
        expectParseException("ThingName thing");
    }

    @Test
    void GIVEN_thingNameWithUnescapedColon_WHEN_RuleExpression_THEN_exceptionIsThrown() {
        expectTokenMgrError("thingName: :");
    }

    @Test
    void GIVEN_thingNameWithNonTrailingWildcard_WHEN_RuleExpression_THEN_exceptionIsThrown() {
        expectParseException("thingName: *thing");
        expectParseException("thingName: thing*2");
    }
}
