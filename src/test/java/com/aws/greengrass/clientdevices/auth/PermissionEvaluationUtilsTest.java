/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth;

import com.aws.greengrass.clientdevices.auth.configuration.AuthorizationPolicyStatement;
import com.aws.greengrass.clientdevices.auth.configuration.GroupConfiguration;
import com.aws.greengrass.clientdevices.auth.configuration.GroupDefinition;
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
import com.aws.greengrass.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class PermissionEvaluationUtilsTest {
    private static final String FAKE_CERT_ID = "FAKE_CERT_ID";
    private static final String THING_NAME = "b";
    private static final String SESSION_ID = "sessionId";
    private static final Set<String> THING_NAME_POLICY_VARIABLE = Collections.singleton("${iot:Connection.Thing.ThingName}");
    Certificate cert;
    Thing thing;
    Session session;
    PermissionEvaluationUtils permissionEvaluationUtils;
    GroupManager groupManager = new GroupManager();

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

    public static Stream<Arguments> invalidAuthRequests() {
        return Stream.of( // operation, resource

                // bad resources
                Arguments.of("mqtt:publish", ""),
                Arguments.of("mqtt:publish", ":"),
                Arguments.of("mqtt:publish", "::"),
                Arguments.of("mqtt:publish", "mqtt:topic:"),
                Arguments.of("mqtt:publish", "mqtt::myTopic"),
                Arguments.of("mqtt:publish", ":topic:myTopic"),
                Arguments.of("mqtt:publish", "mqtt::"),
                Arguments.of("mqtt:publish", "mqtt:"),
                Arguments.of("mqtt:publish", "mqtt: "),
                Arguments.of("mqtt:publish", ":topic:"),
                Arguments.of("mqtt:publish", "::myTopic"),
                Arguments.of("mqtt:publish", "mqtt:topic"),
                Arguments.of("mqtt:publish", "mqtt"),
                Arguments.of("mqtt:publish", "mqtt:topic:myTopic:"),
                Arguments.of("mqtt:publish", "mqtt::topic:myTopic"),
                Arguments.of("mqtt:publish", "mqtt:topic::myTopic"),
                Arguments.of("mqtt:publish", ":mqtt:topic:myTopic"),
                Arguments.of("mqtt:publish", " :topic:myTopic"),
                Arguments.of("mqtt:publish", "mqtt: :myTopic"),

                // bad operations
                Arguments.of("", "mqtt:topic:myTopic"),
                Arguments.of(":", "mqtt:topic:myTopic"),
                Arguments.of("mqtt", "mqtt:topic:myTopic"),
                Arguments.of("mqtt:", "mqtt:topic:myTopic"),
                Arguments.of("mqtt: ", "mqtt:topic:myTopic"),
                Arguments.of(":publish", "mqtt:topic:myTopic"),
                Arguments.of(" :publish", "mqtt:topic:myTopic"),
                Arguments.of("mqtt:publish:", "mqtt:topic:myTopic"),
                Arguments.of("mqtt::publish", "mqtt:topic:myTopic"),
                Arguments.of(":mqtt:publish", "mqtt:topic:myTopic")
        );
    }

    @MethodSource("invalidAuthRequests")
    @ParameterizedTest
    void GIVEN_invalid_auth_request_WHEN_authZ_performed_THEN_exception_thrown(String operation, String resource, ExtensionContext context) {
        ignoreExceptionOfType(context, PolicyException.class);
        assertFalse(permissionEvaluationUtils.isAuthorized(
                AuthorizationRequest.builder()
                        .sessionId(SESSION_ID)
                        .operation(operation)
                        .resource(resource)
                        .build(),
                session));
    }

    public static Stream<Arguments> validPolicies() {
        return Stream.of( // policyOperation, policyResource, requestOperation, requestResource, expectedResult

                // basic
                Arguments.of("mqtt:publish", "mqtt:topic:hello", "mqtt:publish", "mqtt:topic:hello", true),

                // basic - negative cases
                Arguments.of("mqtt:publish", "mqtt:topic:hello", "mqtt:publish", "mqtt:topic:world", false),
                Arguments.of("mqtt:publish", "mqtt:topic:hello", "mqtt:connect", "mqtt:topic:hello", false),

                // resource wildcards
                Arguments.of("mqtt:publish", "mqtt:topic:*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "mqtt:topic*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "mqtt:*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "mqtt*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "*mqtt*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "*topic*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "*hello*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "*:hello", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "mqtt:*:*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "*:*:*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "*:**", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "**:*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "***", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "*:topic:*", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("mqtt:publish", "***************************", "mqtt:publish", "mqtt:topic:hello", true),

                // resource wildcards - negative cases
                Arguments.of("mqtt:publish", "mqtt:topic:*", "mqtt:publish", "mqtt:topic:", false),
                Arguments.of("mqtt:publish", "mqtt:*:*", "mqtt:publish", "mqtt:topic:", false),
                Arguments.of("mqtt:publish", "mqtt:*:*", "mqtt:publish", "mqtt::", false),
                Arguments.of("mqtt:publish", "mqtt:*:hello", "mqtt:publish", "mqtt::hello", false),
                Arguments.of("mqtt:publish", "*:topic:hello", "mqtt:publish", ":topic:hello", false),
                Arguments.of("mqtt:publish", "*:hello", "mqtt:publish", "topic:hello", false),
                Arguments.of("mqtt:publish", "*", "mqtt:publish", "topic", false),
                Arguments.of("mqtt:publish", "*", "mqtt:publish", "mqtt", false),
                Arguments.of("mqtt:publish", "*", "mqtt:publish", "mqtt:topic", false),
                Arguments.of("mqtt:publish", "*", "mqtt:publish", "mqtt:topic:", false),
                Arguments.of("mqtt:publish", "*:*:*", "mqtt:publish", "::", false),
                Arguments.of("mqtt:publish", "mqtt:topic:*", "mqtt:connect", "mqtt:topic:hello", false),

                // operation wildcards
                Arguments.of("mqtt:*", "mqtt:topic:hello", "mqtt:publish", "mqtt:topic:hello", true),
                Arguments.of("*", "mqtt:topic:hello", "mqtt:publish", "mqtt:topic:hello", true),

                // operation wildcards - negative cases
                Arguments.of("mqtt:*", "mqtt:topic:hello", "mqtt:", "mqtt:topic:hello", false),
                Arguments.of("*", "mqtt:topic:hello", "mqtt:", "mqtt:topic:hello", false),
                Arguments.of("*", "mqtt:topic:hello", ":publish", "mqtt:topic:hello", false),
                Arguments.of("*", "mqtt:topic:hello", ":", "mqtt:topic:hello", false),
                Arguments.of("*", "mqtt:topic:hello", "mqtt", "mqtt:topic:hello", false),

                // policy variables
                Arguments.of("mqtt:publish", "mqtt:topic:${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:topic:b", true),
                Arguments.of("mqtt:publish", "mqtt:topic:${iot:Connection.Thing.ThingName}${iot:Connection.Thing.ThingName}${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:topic:bbb", true),
                Arguments.of("mqtt:publish", "mqtt:${iot:Connection.Thing.ThingName}:${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:b:b", true),
                Arguments.of("mqtt:publish", "mqtt:${iot:Connection.Thing.ThingName}:hello", "mqtt:publish", "mqtt:b:hello", true),
                Arguments.of("bbb:publish", "${iot:Connection.Thing.ThingName}${iot:Connection.Thing.ThingName}${iot:Connection.Thing.ThingName}:${iot:Connection.Thing.ThingName}${iot:Connection.Thing.ThingName}${iot:Connection.Thing.ThingName}:${iot:Connection.Thing.ThingName}${iot:Connection.Thing.ThingName}${iot:Connection.Thing.ThingName}", "bbb:publish", "bbb:bbb:bbb", true),
                Arguments.of("b:publish", "${iot:Connection.Thing.ThingName}:${iot:Connection.Thing.ThingName}:${iot:Connection.Thing.ThingName}", "b:publish", "b:b:b", true),
                Arguments.of("b:publish", "${iot:Connection.Thing.ThingName}:${iot:Connection.Thing.ThingName}:hello", "b:publish", "b:b:hello", true),
                Arguments.of("b:publish", "${iot:Connection.Thing.ThingName}:topic:hello", "b:publish", "b:topic:hello", true),

                // policy variables - negative cases
                Arguments.of("mqtt:publish", "mqtt:topic:${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:topic:a", false),
                Arguments.of("mqtt:publish", "mqtt:topic:${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:topic:bb", false),

                // policy variables and wildcards
                Arguments.of("mqtt:publish", "mqtt:*:${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:topic:b", true),
                Arguments.of("mqtt:publish", "mqtt:${iot:Connection.Thing.ThingName}:*", "mqtt:publish", "mqtt:b:topic", true),
                Arguments.of("mqtt:publish", "mqtt:*:*${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:topic:b", true),
                Arguments.of("mqtt:publish", "mqtt:*:*${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:topic:bb", true),
                Arguments.of("mqtt:publish", "mqtt:*:${iot:Connection.Thing.ThingName}*", "mqtt:publish", "mqtt:topic:bb", true),

                // policy variables and wildcards - negative cases
                Arguments.of("mqtt:publish", "mqtt:*:${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:topic:a", false),
                Arguments.of("mqtt:publish", "mqtt:*:${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:topic:bb", false),
                Arguments.of("mqtt:publish", "mqtt:*${iot:Connection.Thing.ThingName}", "mqtt:publish", "mqtt:b", false),

                // special characters
                Arguments.of("mqtt:publish", "mqtt:topic:$foo .10bar/導À-baz/#", "mqtt:publish", "mqtt:topic:$foo .10bar/導À-baz/#", true),
                Arguments.of("mqtt:publish", "mqtt:topic:$foo/bar/+/baz", "mqtt:publish", "mqtt:topic:$foo/bar/+/baz", true),

                // a little bit of everything
                Arguments.of("mqtt:*", "mqtt:*:$*${iot:Connection.Thing.ThingName}*", "mqtt:publish", "mqtt:topic:$導*b*", true)
        );
    }

    @MethodSource("validPolicies")
    @ParameterizedTest
    void GIVEN_valid_policies_WHEN_auth_performed_THEN_authorized(String policyOperation, String policyResource, String requestOperation, String requestResource, boolean expectedResult, ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, PolicyException.class);

        groupManager.setGroupConfiguration(GroupConfiguration.builder()
                .definitions(Utils.immutableMap(
                        "group1", GroupDefinition.builder()
                                .selectionRule("thingName: " + THING_NAME)
                                .policyName("sensor")
                                .build()))
                .policies(Utils.immutableMap(
                        "sensor", Utils.immutableMap(
                                "Statement1", AuthorizationPolicyStatement.builder()
                                        .statementDescription("Policy description")
                                        .effect(AuthorizationPolicyStatement.Effect.ALLOW)
                                        .resources(new HashSet<>(Collections.singleton(policyResource)))
                                        .operations(new HashSet<>(Collections.singleton(policyOperation)))
                                        .build()
                        )))
                .build());

        assertEquals(expectedResult, permissionEvaluationUtils.isAuthorized(
                AuthorizationRequest.builder()
                        .sessionId(SESSION_ID)
                        .operation(requestOperation)
                        .resource(requestResource)
                        .build(),
                session));
    }
}
