/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.SessionImpl;
import com.aws.greengrass.clientdevices.auth.configuration.parser.ParseException;
import com.aws.greengrass.clientdevices.auth.exception.AuthorizationException;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class GroupManagerTest {
    @Test
    void GIVEN_emptyGroupConfiguration_WHEN_getApplicablePolicyPermissions_THEN_returnEmptySet()
            throws AuthorizationException {
        GroupManager groupManager = new GroupManager();
        groupManager.setGroupConfiguration(new GroupConfiguration(null, null, null));

        assertThat(groupManager.getApplicablePolicyPermissions(getSessionFromThing("thingName")),
                is(Collections.emptyMap()));
    }

    @Test
    void GIVEN_sessionInNoGroup_WHEN_getApplicablePolicyPermissions_THEN_returnEmptySet()
            throws AuthorizationException, ParseException {
        GroupConfiguration groupConfiguration = GroupConfiguration.builder()
                .definitions(Collections.singletonMap("group1", getGroupDefinition("differentThingName", "policy1")))
                .policies(Collections.singletonMap("policy1",
                        Collections.singletonMap("Statement1", getPolicyStatement("connect", "clientId")))).build();
        GroupManager groupManager = new GroupManager();
        groupManager.setGroupConfiguration(groupConfiguration);

        assertThat(groupManager.getApplicablePolicyPermissions(getSessionFromThing("thingName")),
                is(Collections.emptyMap()));
    }

    @Test
    void GIVEN_sessionInSingleGroup_WHEN_getApplicablePolicyPermissions_THEN_returnGroupPermissions()
            throws AuthorizationException, ParseException {
        Session session = getSessionFromThing("thingName");
        GroupConfiguration groupConfiguration = GroupConfiguration.builder()
                .definitions(Collections.singletonMap("group1", getGroupDefinition("thingName", "policy1"))).policies(
                        Collections.singletonMap("policy1",
                                Collections.singletonMap("Statement1", getPolicyStatement("connect", "clientId"))))
                .build();
        GroupManager groupManager = new GroupManager();
        Map<String, Set<Permission>> permissionsMap = new HashMap<>(Collections.singletonMap("group1",
                new HashSet<>(Collections.singleton(Permission.builder().principal("group1").operation("connect").resource("clientId")
                        .policyVariables(Collections.emptyList()).build()))));

        groupManager.setGroupConfiguration(groupConfiguration);

        assertThat(groupManager.getApplicablePolicyPermissions(session), is(permissionsMap));
    }

    @Test
    void GIVEN_sessionInMultipleGroups_WHEN_getApplicablePolicyPermissions_THEN_returnMergedGroupPermissions()
            throws AuthorizationException, ParseException {
        Session session = getSessionFromThing("thingName");
        GroupConfiguration groupConfiguration =
                GroupConfiguration.builder().definitions(new HashMap<String, GroupDefinition>() {{
                    put("group1", getGroupDefinition("thingName", "policy1"));
                    put("group2", getGroupDefinition("thingName", "policy2"));
                    put("group3", getGroupDefinition("differentThingName", "policy3"));
                }}).policies(new HashMap<String, Map<String, AuthorizationPolicyStatement>>() {{
                    put("policy1", Collections.singletonMap("Statement1", getPolicyStatement("connect", "clientId")));
                    put("policy2", Collections.singletonMap("Statement1", getPolicyStatement("publish", "topic")));
                    put("policy3", Collections.singletonMap("Statement1", getPolicyStatement("subscribe", "topic")));
                }}).build();
        GroupManager groupManager = new GroupManager();

        Map<String, Set<Permission>> permissionsMap = new HashMap<>();
        permissionsMap.put("group1",
                new HashSet<>(Collections.singleton(Permission.builder().principal("group1").operation("connect").resource("clientId")
                        .policyVariables(Collections.emptyList()).build())));
        permissionsMap.put("group2",
                new HashSet<>(Collections.singleton(Permission.builder().principal("group2").operation("publish").resource("topic")
                        .policyVariables(Collections.emptyList()).build())));

        groupManager.setGroupConfiguration(groupConfiguration);

        assertThat(groupManager.getApplicablePolicyPermissions(session), is(permissionsMap));
    }

    private Session getSessionFromThing(String thingName) {
        Thing thing = Thing.of(thingName);
        return new SessionImpl(thing);
    }

    private GroupDefinition getGroupDefinition(String thingName, String policyName) throws ParseException {
        return GroupDefinition.builder().selectionRule("thingName: " + thingName).policyName(policyName).build();
    }

    private AuthorizationPolicyStatement getPolicyStatement(String operation, String resource) {
        return new AuthorizationPolicyStatement("Policy description", AuthorizationPolicyStatement.Effect.ALLOW,
                new HashSet<>(Collections.singleton(operation)), new HashSet<>(Collections.singleton(resource)));
    }
}
