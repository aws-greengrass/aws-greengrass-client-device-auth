/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.configuration.Permission;
import com.aws.greengrass.clientdevices.auth.exception.PolicyException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateFake;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.SessionImpl;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class PermissionEvaluationUtilsTest {

    private static final String FAKE_CERT_ID = "FAKE_CERT_ID";
    private static final String THING_NAME = "b";
    private static final String SESSION_ID = "sessionId";
    private static final Set<String> THING_NAME_POLICY_VARIABLE = Collections.
            singleton("${iot:Connection.Thing.ThingName}");
    private Certificate cert;
    private Thing thing;
    private Session session;
    private PermissionEvaluationUtils permissionEvaluationUtils;
    @Mock
    private GroupManager groupManager;

    @BeforeEach
    void beforeEach() throws InvalidCertificateException {
        cert = CertificateFake.of(FAKE_CERT_ID);
        thing = Thing.of(THING_NAME);
        session = new SessionImpl(cert, thing);
        permissionEvaluationUtils = new PermissionEvaluationUtils(groupManager);
    }

    @Test
    void GIVEN_single_permission_with_policy_variable_WHEN_get_permission_resource_THEN_return_updated_permission_resource()
            throws PolicyException {
        Permission policyVariablePermission =
                Permission.builder().principal("some-principal").operation("some-operation")
                        .resource("/msg/${iot:Connection.Thing.ThingName}").resourcePolicyVariables(THING_NAME_POLICY_VARIABLE).build();

        Permission permission = Permission.builder().principal("some-principal").operation("some-operation")
                .resource("/msg/b").build();
        assertThat(policyVariablePermission.getResource(session).equals(permission.getResource()), is(true));
    }

    @Test
    void GIVEN_single_permission_with_invalid_policy_variable_WHEN_get_permission_resource_THEN_return_updated_permission_resource()
            throws PolicyException {
        Permission policyVariablePermission =
                Permission.builder().principal("some-principal").operation("some-operation")
                        .resource("/msg/${iot:Connection.Thing.ThingName/}").build();

        Permission permission = Permission.builder().principal("some-principal").operation("some-operation")
                .resource("/msg/b").build();

        assertThat(policyVariablePermission.getResource(session).equals(policyVariablePermission.getResource()), is(true));
        assertThat(policyVariablePermission.getResource(session).equals(permission.getResource()), is(false));
    }

    @Test
    void GIVEN_single_permission_with_nonexistent_policy_variable_WHEN_get_permission_resource_THEN_return_updated_permission_resource()
            throws PolicyException {
        Permission policyVariablePermission =
                Permission.builder().principal("some-principal").operation("some-operation")
                        .resource("/msg/${iot:Connection.Thing.RealThing}").build();

        Permission permission = Permission.builder().principal("some-principal").operation("some-operation")
                .resource("/msg/b").build();

        assertThat(policyVariablePermission.getResource(session).equals(policyVariablePermission.getResource()), is(true));
        assertThat(policyVariablePermission.getResource(session).equals(permission.getResource()), is(false));
    }

    @Test
    void GIVEN_single_permission_with_multiple_policy_variables_WHEN_get_permission_resource_THEN_return_updated_permission_resource()
            throws PolicyException {
        Permission policyVariablePermission =
                Permission.builder().principal("some-principal").operation("some-operation")
                        .resource("/msg/${iot:Connection.Thing.ThingName}/${iot:Connection.Thing.ThingName}/src")
                        .resourcePolicyVariables(THING_NAME_POLICY_VARIABLE).build();

        Permission permission = Permission.builder().principal("some-principal").operation("some-operation")
                .resource("/msg/b/b/src").build();

        assertThat(policyVariablePermission.getResource(session).equals(policyVariablePermission.getResource()), is(false));
        assertThat(policyVariablePermission.getResource(session).equals(permission.getResource()), is(true));
    }

    @Test
    void GIVEN_single_permission_with_multiple_invalid_policy_variables_WHEN_get_permission_resource_THEN_return_updated_permission_resource()
            throws PolicyException {
        Permission policyVariablePermission =
                Permission.builder().principal("some-principal").operation("some-operation")
                        .resource("/msg/${iot:Connection.Thing.ThingName}/${iot:Connection}.Thing.RealThing}/src")
                        .resourcePolicyVariables(THING_NAME_POLICY_VARIABLE).build();

        Permission permission = Permission.builder().principal("some-principal").operation("some-operation")
                .resource("/msg/b/b/src").build();

        Permission expectedPermission = Permission.builder().principal("some-principal").operation("some-operation")
                .resource("/msg/b/${iot:Connection}.Thing.RealThing}/src").build();

        assertThat(policyVariablePermission.getResource(session).equals(policyVariablePermission.getResource()), is(false));
        assertThat(policyVariablePermission.getResource(session).equals(permission.getResource()), is(false));
        assertThat(policyVariablePermission.getResource(session).equals(expectedPermission.getResource()), is(true));
    }

    @Test
    void GIVEN_single_group_permission_with_variable_WHEN_evaluate_operation_permission_THEN_return_decision() {
        when(groupManager.getApplicablePolicyPermissions(any(Session.class)))
                .thenReturn(prepareGroupVariablePermissionsData());

        AuthorizationRequest request = AuthorizationRequest.builder().operation("mqtt:publish")
                .resource("mqtt:topic:a").sessionId(SESSION_ID).build();
        boolean authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:publish").resource("mqtt:topic:b")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:subscribe").resource("mqtt:topic:b")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:connect").resource("mqtt:broker:localBroker")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:subscribe")
                .resource("mqtt:topic:device:${iot:Connection.FakeThing.ThingName}").sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:publish").resource("mqtt:topic:d")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(false));

        request = AuthorizationRequest.builder().operation("mqtt:subscribe").resource("mqtt:message:a")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(false));

        request = AuthorizationRequest.builder().operation("mqtt:subscribe").resource("mqtt:topic:device:b")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(false));
    }

    @Test
    void GIVEN_single_group_permission_WHEN_evaluate_operation_permission_THEN_return_decision() {
        when(groupManager.getApplicablePolicyPermissions(any(Session.class))).thenReturn(prepareGroupPermissionsData());

        AuthorizationRequest request = AuthorizationRequest.builder().operation("mqtt:publish")
                .resource("mqtt:topic:a").sessionId(SESSION_ID).build();
        boolean authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:publish").resource("mqtt:topic:b")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:subscribe").resource("mqtt:topic:b")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:subscribe").resource("mqtt:topic:$foo/bar/+/baz")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:subscribe")
                .resource("mqtt:topic:$foo .10bar/導À-baz/#").sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:connect").resource("mqtt:broker:localBroker")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(true));

        request = AuthorizationRequest.builder().operation("mqtt:publish").resource("mqtt:topic:d")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(false));

        request = AuthorizationRequest.builder().operation("mqtt:subscribe").resource("mqtt:message:a")
                .sessionId(SESSION_ID).build();
        authorized = permissionEvaluationUtils.isAuthorized(request, session);
        assertThat(authorized, is(false));
    }

    private Map<String, Set<Permission>> prepareGroupPermissionsData() {
        Permission[] sensorPermission =
                {Permission.builder().principal("sensor").operation("mqtt:publish").resource("mqtt:topic:a").build(),
                        Permission.builder().principal("sensor").operation("mqtt:*").resource("mqtt:topic:b").build(),
                        Permission.builder().principal("sensor").operation("mqtt:subscribe")
                                .resource("mqtt:topic:*").build(),
                        Permission.builder().principal("sensor").operation("mqtt:connect").resource("*").build(),};
        return Collections.singletonMap("sensor", new HashSet<>(Arrays.asList(sensorPermission)));
    }

    private Map<String, Set<Permission>> prepareGroupVariablePermissionsData() {
        Permission[] sensorPermission =
                {Permission.builder().principal("sensor").operation("mqtt:publish").resource("mqtt:topic:a").build(),
                        Permission.builder().principal("sensor").operation("mqtt:*").resource("mqtt:topic:${iot:Connection.Thing.ThingName}")
                                .resourcePolicyVariables(THING_NAME_POLICY_VARIABLE).build(),
                        Permission.builder().principal("sensor").operation("mqtt:subscribe")
                                .resource("mqtt:topic:device:${iot:Connection.FakeThing.ThingName}").build(),
                        Permission.builder().principal("sensor").operation("mqtt:connect").resource("*").build(),};
        return Collections.singletonMap("sensor", new HashSet<>(Arrays.asList(sensorPermission)));
    }

}
