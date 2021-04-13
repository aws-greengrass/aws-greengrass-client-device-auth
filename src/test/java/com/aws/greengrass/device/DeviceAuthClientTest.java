/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.configuration.Permission;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.iot.Thing;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.hamcrest.collection.IsMapContaining;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class DeviceAuthClientTest {

    @InjectMocks
    private DeviceAuthClient authClient;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private GroupManager groupManager;

    @Mock
    private IotAuthClient iotClient;

    @Test
    void GIVEN_emptySessionManager_WHEN_createSession_THEN_sessionReturned() {
        DeviceAuthClient deviceAuthClient = new DeviceAuthClient(new SessionManager(), new GroupManager(), iotClient);
        String sessionId = deviceAuthClient.createSession("FAKE_PEM");
        assertThat(sessionId, not(emptyOrNullString()));
    }

    @Test
    void GIVEN_invalidSessionId_WHEN_canDevicePerform_THEN_authorizationExceptionThrown() {
        DeviceAuthClient deviceAuthClient = new DeviceAuthClient(new SessionManager(), new GroupManager(), iotClient);
        String sessionId = "FAKE_SESSION";
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest("mqtt:connect", "mqtt:clientId:clientId", sessionId, "clientId");
        Assertions.assertThrows(AuthorizationException.class,
                () -> deviceAuthClient.canDevicePerform(authorizationRequest));
    }

    @Test
    void GIVEN_missingDevicePermission_WHEN_canDevicePerform_THEN_authorizationExceptionThrown()
            throws AuthorizationException {
        DeviceAuthClient deviceAuthClient = new DeviceAuthClient(new SessionManager(), new GroupManager(), iotClient);
        String sessionId = deviceAuthClient.createSession("FAKE_PEM");
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest("mqtt:connect", "mqtt:clientId:clientId", sessionId, "clientId");
        assertThat(deviceAuthClient.canDevicePerform(authorizationRequest), is(false));
    }

    @Test
    void GIVEN_session_no_thing_WHEN_receive_authorization_request_THEN_thing_added_to_session() throws Exception {
        Session session = new Session(new Certificate("certificatePem", iotClient));
        when(sessionManager.findSession("sessionId")).thenReturn(session);
        when(iotClient.isThingAttachedToCertificate(any(), any())).thenReturn(true);
        when(groupManager.getApplicablePolicyPermissions(session)).thenReturn(Collections.singletonMap("group1",
                Collections.singleton(
                        Permission.builder().operation("mqtt:publish").resource("mqtt:topic:foo").principal("group1")
                                .build())));

        boolean authorized = authClient.canDevicePerform(constructAuthorizationRequest());

        assertThat(authorized, is(true));
        assertThat(session, IsMapContaining.hasKey("Thing"));
        assertThat(session.getSessionAttribute("Thing", "ThingName").toString(), is("clientId"));
        ArgumentCaptor<Thing> thingArgumentCaptor = ArgumentCaptor.forClass(Thing.class);
        ArgumentCaptor<Certificate> certificateArgumentCaptor = ArgumentCaptor.forClass(Certificate.class);
        verify(iotClient)
                .isThingAttachedToCertificate(thingArgumentCaptor.capture(), certificateArgumentCaptor.capture());
        assertThat(thingArgumentCaptor.getValue().getThingName(), is("clientId"));
        assertThat(certificateArgumentCaptor.getValue().getCertificatePem(), is("certificatePem"));
    }

    @Test
    void GIVEN_session_has_thing_WHEN_receive_authorization_request_THEN_thing_in_session_not_changed()
            throws Exception {
        Session session = new Session(new Certificate("certificatePem", iotClient));
        session.put(Thing.NAMESPACE, new Thing("baz"));
        when(sessionManager.findSession("sessionId")).thenReturn(session);
        when(groupManager.getApplicablePolicyPermissions(session)).thenReturn(Collections.singletonMap("group1",
                Collections.singleton(
                        Permission.builder().operation("mqtt:publish").resource("mqtt:topic:bar").principal("group1")
                                .build())));

        boolean authorized = authClient.canDevicePerform(constructAuthorizationRequest());

        assertThat(authorized, is(false));
        assertThat(session, IsMapContaining.hasKey("Thing"));
        assertThat(session.getSessionAttribute("Thing", "ThingName").toString(), is("baz"));
        verify(iotClient, never()).isThingAttachedToCertificate(any(), any());
    }

    private AuthorizationRequest constructAuthorizationRequest() {
        return AuthorizationRequest.builder().sessionId("sessionId").clientId("clientId").operation("mqtt:publish")
                .resource("mqtt:topic:foo").build();
    }
}
