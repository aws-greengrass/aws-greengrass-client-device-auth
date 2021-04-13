/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration;

import com.aws.greengrass.device.Session;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.attribute.StringLiteralAttribute;
import com.aws.greengrass.device.configuration.parser.ParseException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class GroupDefinitionTest {
    @Mock
    private IotAuthClient mockIotClient;

    @Test
    public void GIVEN_groupDefinitionAndMatchingSession_WHEN_containsSession_THEN_returnsTrue() throws ParseException {
        GroupDefinition groupDefinition = new GroupDefinition("thingName: thing", "Policy1");
        Session session = Mockito.mock(Session.class);
        DeviceAttribute attribute = new StringLiteralAttribute("thing");
        Mockito.when(session.getSessionAttribute(any(), any())).thenReturn(attribute);
        Assertions.assertTrue(groupDefinition.containsClientDevice(session));
    }

    @Test
    public void GIVEN_groupDefinitionAndNonMatchingSession_WHEN_containsSession_THEN_returnsFalse() throws ParseException {
        GroupDefinition groupDefinition = new GroupDefinition("thingName: thing", "Policy1");
        Assertions.assertFalse(groupDefinition.containsClientDevice(
                new Session(new Certificate("FAKE PEM", mockIotClient))));
    }
}
