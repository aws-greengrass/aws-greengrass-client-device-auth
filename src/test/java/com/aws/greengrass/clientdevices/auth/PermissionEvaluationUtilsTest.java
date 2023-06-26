/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.configuration.Permission;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateFake;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.SessionImpl;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class PermissionEvaluationUtilsTest {

    @Test
    void GIVEN_single_group_permission_with_policy_variable_WHEN_update_resource_permission_THEN_return_updated_permission() throws InvalidCertificateException {
        Certificate cert = CertificateFake.of("FAKE_CERT_ID");
        Thing thing = Thing.of("b");
        Session session = new SessionImpl(cert, thing);

        Permission policyVariablePermission =
                Permission.builder().principal("some-principal").operation("some-operation").resource("/msg/${iot:Connection.Thing.ThingName}").build();

        Permission updatedPolicyVariablePermission = PermissionEvaluationUtils.updateResource(session, policyVariablePermission);

        Permission permission = Permission.builder().principal("some-principal").operation("some-operation").resource("/msg/b").build();

        assertThat(updatedPolicyVariablePermission.equals(permission), is(true));
    }

    @Test
    void GIVEN_single_group_permission_with_invalid_policy_variable_WHEN_update_resource_permission_THEN_return_updated_permission() throws InvalidCertificateException {
        Certificate cert = CertificateFake.of("FAKE_CERT_ID");
        Thing thing = Thing.of("b");
        Session session = new SessionImpl(cert, thing);

        Permission policyVariablePermission =
                Permission.builder().principal("some-principal").operation("some-operation").resource("/msg/${iot:Connection.Thing.ThingName/}").build();

        Permission updatedPolicyVariablePermission = PermissionEvaluationUtils.updateResource(session, policyVariablePermission);

        Permission permission = Permission.builder().principal("some-principal").operation("some-operation").resource("/msg/b").build();

        assertThat(updatedPolicyVariablePermission.equals(policyVariablePermission), is(true));

        assertThat(updatedPolicyVariablePermission.equals(permission), is(false));
    }

    @Test
    void GIVEN_single_group_permission_with_nonexistent_policy_variable_WHEN_update_resource_permission_THEN_return_updated_permission() throws InvalidCertificateException {
        Certificate cert = CertificateFake.of("FAKE_CERT_ID");
        Thing thing = Thing.of("b");
        Session session = new SessionImpl(cert, thing);

        Permission policyVariablePermission =
                Permission.builder().principal("some-principal").operation("some-operation").resource("/msg/${iot:Connection.Thing.RealThing}").build();

        Permission updatedPolicyVariablePermission = PermissionEvaluationUtils.updateResource(session, policyVariablePermission);

        Permission permission = Permission.builder().principal("some-principal").operation("some-operation").resource("/msg/b").build();

        assertThat(updatedPolicyVariablePermission.equals(policyVariablePermission), is(true));

        assertThat(updatedPolicyVariablePermission.equals(permission), is(false));
    }

    @Test
    void GIVEN_single_group_permission_WHEN_evaluate_operation_permission_THEN_return_decision() {
        Map<String, Set<Permission>> groupPermissions = prepareGroupPermissionsData();
        boolean authorized = PermissionEvaluationUtils.isAuthorized("mqtt:publish", "mqtt:topic:a", groupPermissions);
        assertThat(authorized, is(true));

        authorized = PermissionEvaluationUtils.isAuthorized("mqtt:publish", "mqtt:topic:b", groupPermissions);
        assertThat(authorized, is(true));

        authorized = PermissionEvaluationUtils.isAuthorized("mqtt:subscribe", "mqtt:topic:b", groupPermissions);
        assertThat(authorized, is(true));

        authorized =
                PermissionEvaluationUtils.isAuthorized("mqtt:subscribe", "mqtt:topic:$foo/bar/+/baz", groupPermissions);
        assertThat(authorized, is(true));

        authorized = PermissionEvaluationUtils.isAuthorized("mqtt:subscribe", "mqtt:topic:$foo .10bar/導À-baz/#",
                groupPermissions);
        assertThat(authorized, is(true));

        authorized =
                PermissionEvaluationUtils.isAuthorized("mqtt:connect", "mqtt:broker:localBroker", groupPermissions);
        assertThat(authorized, is(true));

        authorized = PermissionEvaluationUtils.isAuthorized("mqtt:publish", "mqtt:topic:d", groupPermissions);
        assertThat(authorized, is(false));

        authorized = PermissionEvaluationUtils.isAuthorized("mqtt:subscribe", "mqtt:message:a", groupPermissions);
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

}
