/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateFake;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.configuration.Permission;
import com.aws.greengrass.clientdevices.auth.exception.AuthorizationException;
import com.aws.greengrass.clientdevices.auth.iot.Component;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.SessionImpl;
import com.aws.greengrass.clientdevices.auth.session.SessionManager;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.Coerce;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class DeviceAuthClientTest {
    private static final List<String> THING_NAME_POLICY_VARIABLE = Collections
            .singletonList("${iot:Connection.Thing.ThingName}");
    private static final String SESSION_ID = "sessionId";
    private DeviceAuthClient authClient;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private GroupManager groupManager;

    private PermissionEvaluationUtils permissionEvaluationUtils;

    @Mock
    private CertificateStore certificateStore;

    private Topics configurationTopics;

    @BeforeEach
    void beforeEach() {
        configurationTopics = Topics.of(new Context(), KernelConfigResolver.CONFIGURATION_CONFIG_KEY, null);
        permissionEvaluationUtils = new PermissionEvaluationUtils(groupManager);
        authClient = new DeviceAuthClient(sessionManager, certificateStore, permissionEvaluationUtils);
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    void GIVEN_invalidSessionId_WHEN_canDevicePerform_THEN_authorizationExceptionThrown() {
        when(sessionManager.findSession(SESSION_ID)).thenReturn(null);
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest("mqtt:connect", "mqtt:clientId:clientId", SESSION_ID);
        assertThrows(AuthorizationException.class, () -> authClient.canDevicePerform(authorizationRequest));
    }

    @Test
    void GIVEN_missingDevicePermission_WHEN_canDevicePerform_THEN_authorizationReturnFalse() throws Exception {
        Session session = new SessionImpl();
        when(sessionManager.findSession(SESSION_ID)).thenReturn(session);
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest("mqtt:connect", "mqtt:clientId:clientId", SESSION_ID);
        assertThat(authClient.canDevicePerform(authorizationRequest), is(false));
    }

    @Test
    void GIVEN_sessionHasPermission_WHEN_canDevicePerform_THEN_authorizationReturnTrue() throws Exception {
        Session session = new SessionImpl();
        when(sessionManager.findSession(SESSION_ID)).thenReturn(session);
        when(groupManager.getApplicablePolicyPermissions(session)).thenReturn(Collections.singletonMap("group1",
                Collections.singleton(
                        Permission.builder().operation("mqtt:publish").resource("mqtt:topic:foo").principal("group1")
                                .build())));

        boolean authorized = authClient.canDevicePerform(constructAuthorizationRequest());

        assertThat(authorized, is(true));
    }

    @Test
    void GIVEN_sessionHasPolicyVariablesPermission_WHEN_canDevicePerform_THEN_authorizationReturnTrue() throws Exception {
        Certificate cert = CertificateFake.of("FAKE_CERT_ID");
        Thing thing = Thing.of("b");
        Session session = new SessionImpl(cert, thing);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(session);

        String thingName = Coerce.toString(session.getSessionAttribute("Thing", "ThingName"));
        when(groupManager.getApplicablePolicyPermissions(session)).thenReturn(Collections.singletonMap("group1",
                Collections.singleton(Permission.builder().operation("mqtt:publish")
                                .resource("mqtt:topic:${iot:Connection.Thing.ThingName}").principal("group1")
                        .resourcePolicyVariables(THING_NAME_POLICY_VARIABLE).build())));

        boolean authorized = authClient.canDevicePerform(constructPolicyVariableAuthorizationRequest(thingName));

        assertThat(authorized, is(true));
    }

    @Test
    void GIVEN_internalClientSession_WHEN_canDevicePerform_THEN_authorizationReturnTrue() throws Exception {
        Session session = new SessionImpl(new Component());
        when(sessionManager.findSession(SESSION_ID)).thenReturn(session);

        boolean authorized = authClient.canDevicePerform(constructAuthorizationRequest());

        assertThat(authorized, is(true));
    }

    private AuthorizationRequest constructAuthorizationRequest() {
        return AuthorizationRequest.builder().sessionId(SESSION_ID).operation("mqtt:publish")
                .resource("mqtt:topic:foo").build();
    }

    private AuthorizationRequest constructPolicyVariableAuthorizationRequest(String thingName) {
        return AuthorizationRequest.builder().sessionId(SESSION_ID).operation("mqtt:publish")
                .resource(String.format("mqtt:topic:%s", thingName)).build();
    }
}
