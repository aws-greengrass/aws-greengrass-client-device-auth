/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class ThingTest {
    @Test
    public void GIVEN_validThingName_WHEN_Thing_THEN_objectIsCreated() {
        Assertions.assertDoesNotThrow(() -> new Thing("abcdefghijklmnopqrstuvwxyz:_-"));
        Assertions.assertDoesNotThrow(() -> new Thing("ABCDEFGHIJKLMNOPQRSTUXWXYZ"));
        Assertions.assertDoesNotThrow(() -> new Thing("0123456789"));
    }

    @Test
    public void GIVEN_emptyThingName_WHEN_Thing_THEN_exceptionThrown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing(""));
    }

    @Test
    public void GIVEN_thingNameWithInvalidCharacters_WHEN_Thing_THEN_exceptionThrown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing!"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing@"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing#"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing$"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing%"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing^"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing&"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing*"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing("));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Thing("Thing)"));
    }
}
