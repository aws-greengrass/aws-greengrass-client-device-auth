/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.dcm;

import com.aws.iot.evergreen.dependency.State;
import com.aws.iot.evergreen.deployment.DeviceConfiguration;
import com.aws.iot.evergreen.iot.IotConnectionManager;
import com.aws.iot.evergreen.mqtt.MqttClient;
import com.aws.iot.evergreen.testcommons.testutilities.EGExtension;
import com.aws.iot.evergreen.testcommons.testutilities.EGServiceTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, EGExtension.class})
public class DCMServiceTest extends EGServiceTestUtil {
    @Mock
    IotConnectionManager mockIotConnectionManager;

    @Mock
    ExecutorService mockExecutorService;

    @Mock
    DeviceConfiguration mockDeviceConfiguration;

    @Mock
    MqttClient mockMqttClient;

    @BeforeEach
    public void setup() {
        // initialize Evergreen service specific mocks
        serviceFullName = "TokenExchangeService";
        initializeMockedConfig();
        when(stateTopic.getOnce()).thenReturn(State.INSTALLED);
    }

    @Test
    public void GIVEN_dcm_service_WHEN_started_THEN_does_not_throw() {
        //TODO: add more tests
        DCMService dcmService =
                new DCMService(config, mockIotConnectionManager, mockExecutorService, mockDeviceConfiguration,
                        mockMqttClient);
        dcmService.postInject();
        dcmService.startup();
        dcmService.shutdown();
    }
}
