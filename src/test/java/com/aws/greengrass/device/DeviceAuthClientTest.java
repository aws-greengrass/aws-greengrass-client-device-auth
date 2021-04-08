/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.iot.IotControlPlaneBetaClient;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class DeviceAuthClientTest {
    @Mock
    private IotControlPlaneBetaClient mockIotClient;

    @Test
    public void GIVEN_emptySessionManager_WHEN_createSession_THEN_sessionReturned() {
        DeviceAuthClient deviceAuthClient = new DeviceAuthClient(new SessionManager(), new GroupManager(), mockIotClient);
        String sessionId = deviceAuthClient.createSession("FAKE_PEM");
        assertThat(sessionId, not(emptyOrNullString()));
    }

    @Test
    public void GIVEN_invalidSessionId_WHEN_canDevicePerform_THEN_authorizationExceptionThrown() {
        DeviceAuthClient deviceAuthClient = new DeviceAuthClient(new SessionManager(), new GroupManager(), mockIotClient);
        String sessionId = "FAKE_SESSION";
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest("mqtt:connect", "mqtt:clientId:clientId", sessionId, "clientId");
        Assertions.assertThrows(AuthorizationException.class,
                () -> deviceAuthClient.canDevicePerform(authorizationRequest));
    }

    @Test
    public void GIVEN_missingDevicePermission_WHEN_canDevicePerform_THEN_authorizationExceptionThrown() throws AuthorizationException {
        DeviceAuthClient deviceAuthClient = new DeviceAuthClient(new SessionManager(), new GroupManager(), mockIotClient);
        String sessionId = deviceAuthClient.createSession("FAKE_PEM");
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest("mqtt:connect", "mqtt:clientId:clientId", sessionId, "clientId");
        assertThat(deviceAuthClient.canDevicePerform(authorizationRequest), is(false));
    }
}
