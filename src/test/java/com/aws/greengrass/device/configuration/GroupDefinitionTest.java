/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.configuration;

import com.aws.greengrass.device.Session;
import com.aws.greengrass.device.attribute.DeviceAttribute;
import com.aws.greengrass.device.attribute.WildcardSuffixAttribute;
import com.aws.greengrass.device.configuration.parser.ParseException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class GroupDefinitionTest {

    @Test
    void GIVEN_groupDefinitionAndMatchingSession_WHEN_containsSession_THEN_returnsTrue() throws ParseException {
        GroupDefinition groupDefinition = new GroupDefinition("thingName: thing", "Policy1");
        Session session = Mockito.mock(Session.class);
        DeviceAttribute attribute = new WildcardSuffixAttribute("thing");
        Mockito.when(session.getSessionAttribute(any(), any())).thenReturn(attribute);
        assertThat(groupDefinition.containsClientDevice(session), is(true));
    }

    @Test
    void GIVEN_groupDefinitionWithWildcardAndMatchingSession_WHEN_containsSession_THEN_returnsTrue() throws ParseException {
        GroupDefinition groupDefinition = new GroupDefinition("thingName: thing*", "Policy1");
        Session session = Mockito.mock(Session.class);
        DeviceAttribute attribute = new WildcardSuffixAttribute("thing-A");
        Mockito.when(session.getSessionAttribute(any(), any())).thenReturn(attribute);
        assertThat(groupDefinition.containsClientDevice(session), is(true));
    }

    @Test
    void GIVEN_groupDefinitionAndNonMatchingSession_WHEN_containsSession_THEN_returnsFalse() throws ParseException {
        GroupDefinition groupDefinition = new GroupDefinition("thingName: thing", "Policy1");
        assertThat(groupDefinition.containsClientDevice(
                new Session(new Certificate("FAKE_PEM_HASH", "FAKE_CERT_ID"))), is(false));
    }
}
