/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.steps;

import com.aws.greengrass.testing.api.model.TestId;
import com.aws.greengrass.testing.features.IotSteps;
import com.aws.greengrass.testing.model.ScenarioContext;
import com.aws.greengrass.testing.model.TestContext;
import com.aws.greengrass.testing.resources.AWSResources;
import com.aws.greengrass.testing.resources.iot.IotPolicySpec;
import com.aws.greengrass.testing.resources.iot.IotThingSpec;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttControlStepsTest {

    @Mock
    private TestContext testContext;

    @Mock
    private ScenarioContext scenarioContext;

    @Mock
    private AWSResources resources;

    @Mock
    private IotSteps iotSteps;

    @InjectMocks
    MqttControlSteps mqttControlSteps;

    @Test
    void WHEN_create_new_thing_THEN_it_works_as_expected() {
        val clientDeviceId = "testThing";
        val testThingName = "test-testThing";
        val testId = mock(TestId.class);
        val iotPolicySpec = mock(IotPolicySpec.class);
        val captor = ArgumentCaptor.forClass(IotThingSpec.class);

        when(testContext.testId()).thenReturn(testId);
        when(testId.idFor(anyString())).thenReturn(testThingName);
        when(iotSteps.createDefaultPolicy(anyString())).thenReturn(iotPolicySpec);

        mqttControlSteps.createClientDevice(clientDeviceId);

        verify(scenarioContext, times(1)).put(clientDeviceId, testThingName);
        verify(iotSteps, times(1)).createDefaultPolicy(clientDeviceId);
        verify(resources, times(1)).create(captor.capture());

        val iotThingSpec = captor.getValue();
        assertEquals(testThingName, iotThingSpec.thingName());
        assertTrue(iotThingSpec.createCertificate());
        assertEquals(testThingName, iotThingSpec.certificateSpec().thingName());
        assertEquals(iotPolicySpec, iotThingSpec.policySpec());
    }

}
