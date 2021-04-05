/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.attribute;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
public class StringLiteralAttributeTest {
    @Test
    public void GIVEN_emptyString_WHEN_matchesEmptyString_THEN_returnsTrue() {
        DeviceAttribute attribute = new StringLiteralAttribute("");
        Assertions.assertTrue(attribute.matches(""));
    }

    @Test
    public void GIVEN_emptyString_WHEN_matchesNull_THEN_returnsFalse() {
        DeviceAttribute attribute = new StringLiteralAttribute("");
        Assertions.assertFalse(attribute.matches(null));
    }

    @Test
    public void GIVEN_nonEmptyString_WHEN_matchesNonEmptyString_THEN_returnsTrue() {
        DeviceAttribute attribute = new StringLiteralAttribute("Value");
        Assertions.assertTrue(attribute.matches("Value"));
    }

    @Test
    public void GIVEN_nonEmptyString_WHEN_matchesNull_THEN_returnsFalse() {
        DeviceAttribute attribute = new StringLiteralAttribute("Value");
        Assertions.assertFalse(attribute.matches(null));
    }

    @Test
    public void GIVEN_nonEmptyString_WHEN_matchesStringPrefix_THEN_returnsFalse() {
        DeviceAttribute attribute = new StringLiteralAttribute("Value");
        Assertions.assertFalse(attribute.matches("Valu"));
    }

    @Test
    public void GIVEN_nonEmptyString_WHEN_matchesStringPrefixWithWildcard_THEN_returnsFalse() {
        DeviceAttribute attribute = new StringLiteralAttribute("Value");
        Assertions.assertFalse(attribute.matches("Valu*"));
    }
}
