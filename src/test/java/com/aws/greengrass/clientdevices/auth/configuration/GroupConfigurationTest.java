/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;


import com.aws.greengrass.clientdevices.auth.exception.PolicyException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class GroupConfigurationTest {

    public static Stream<Arguments> validResources() {
        return Stream.of( // resource, expected variables
                Arguments.of("mqtt:topic:hello", Collections.emptySet()),
                Arguments.of("mqtt:topic:${iot:Connection.Thing.ThingName}", Collections.singleton("${iot:Connection.Thing.ThingName}")),
                Arguments.of("mqtt:topic:${ioT:ConneCtion.tHing.thingName}", Collections.singleton("${iot:Connection.Thing.ThingName}")),
                Arguments.of("mqtt:topic:${iot:Connection.Thing.ThingName}}", Collections.singleton("${iot:Connection.Thing.ThingName}")),
                Arguments.of("mqtt:topic:{{${iot:Connection.Thing.ThingName}}", Collections.singleton("${iot:Connection.Thing.ThingName}")),
                Arguments.of("mqtt:topic:{${iot:Connection.Thing.ThingName}}", Collections.singleton("${iot:Connection.Thing.ThingName}"))
        );
    }

    @ParameterizedTest
    @MethodSource("validResources")
    void GIVEN_group_configuration_with_valid_resource_WHEN_validate_THEN_success(String resource, Set<String> expectedVariables) throws Exception {
        GroupConfiguration configuration = GroupConfiguration.builder()
                .definitions(Utils.immutableMap(
                        "group1", GroupDefinition.builder()
                                .selectionRule("thingName: myThing")
                                .policyName("sensor")
                                .build()))
                .policies(Utils.immutableMap(
                        "sensor", Utils.immutableMap(
                                "Statement1", AuthorizationPolicyStatement.builder()
                                        .statementDescription("Policy description")
                                        .effect(AuthorizationPolicyStatement.Effect.ALLOW)
                                        .resources(Collections.singleton(resource))
                                        .operations(Collections.singleton("mqtt:publish"))
                                        .build()
                        )))
                .build();
        configuration.validate();
        // verify expected variables are in policy
        assertEquals(
                expectedVariables.stream().map(String::toLowerCase).collect(Collectors.toSet()),
                configuration.getGroupToPermissionsMap().values().stream()
                        .flatMap(permissions -> permissions.stream()
                                .flatMap(p -> p.getResourcePolicyVariables().stream().map(String::toLowerCase)))
                        .collect(Collectors.toSet()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mqtt:topic:${}",
            "mqtt:topic:${ }",
            "mqtt:topic:${iot}",
            "mqtt:topic:${iot:Connection.Thing.ThingNames}",
            "mqtt:topic:${iot:Connection.Thing.*}",
            "mqtt:topic:${iot:Connection.Thing.ThingName${iot:Connection.Thing.ThingName}}",
            "mqtt:topic:${${iot:Connection.Thing.ThingName}}",
            "mqtt:topic:${*${iot:Connection.Thing.ThingName}}",
            "mqtt:topic:${*}",
    })
    void GIVEN_group_configuration_with_invalid_resource_WHEN_validate_THEN_exception_thrown(String resource) {
        assertThrows(PolicyException.class, () -> GroupConfiguration.builder()
                .definitions(Utils.immutableMap(
                        "group1", GroupDefinition.builder()
                                .selectionRule("thingName: myThing")
                                .policyName("sensor")
                                .build()))
                .policies(Utils.immutableMap(
                        "sensor", Utils.immutableMap(
                                "Statement1", AuthorizationPolicyStatement.builder()
                                        .statementDescription("Policy description")
                                        .effect(AuthorizationPolicyStatement.Effect.ALLOW)
                                        .resources(Collections.singleton(resource))
                                        .operations(Collections.singleton("mqtt:publish"))
                                        .build()
                        )))
                .build()
                .validate());
    }

    @Test
    void GIVEN_group_configuration_with_mismatched_definition_WHEN_validate_THEN_exception_thrown() {
        assertThrows(PolicyException.class, () -> GroupConfiguration.builder()
                .definitions(Utils.immutableMap(
                        "group1", GroupDefinition.builder()
                                .selectionRule("thingName: myThing")
                                .policyName("sensor")
                                .build()))
                .policies(Utils.immutableMap(
                        "notSensor", Utils.immutableMap(
                                "Statement1", AuthorizationPolicyStatement.builder()
                                        .statementDescription("Policy description")
                                        .effect(AuthorizationPolicyStatement.Effect.ALLOW)
                                        .resources(Collections.singleton("mqtt:topic:hello"))
                                        .operations(Collections.singleton("mqtt:publish"))
                                        .build()
                        )))
                .build()
                .validate());
    }
}
