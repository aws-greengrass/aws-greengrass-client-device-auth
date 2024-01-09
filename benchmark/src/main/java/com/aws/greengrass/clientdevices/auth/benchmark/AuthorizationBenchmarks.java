/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.benchmark;

import com.aws.greengrass.clientdevices.auth.AuthorizationRequest;
import com.aws.greengrass.clientdevices.auth.DeviceAuthClient;
import com.aws.greengrass.clientdevices.auth.configuration.AuthorizationPolicyStatement;
import com.aws.greengrass.clientdevices.auth.configuration.GroupConfiguration;
import com.aws.greengrass.clientdevices.auth.configuration.GroupDefinition;
import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.configuration.parser.ParseException;
import com.aws.greengrass.clientdevices.auth.exception.AuthorizationException;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.SessionManager;
import com.aws.greengrass.clientdevices.auth.session.attribute.AttributeProvider;
import com.aws.greengrass.clientdevices.auth.session.attribute.DeviceAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.StringLiteralAttribute;
import com.aws.greengrass.clientdevices.auth.session.attribute.WildcardSuffixAttribute;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class AuthorizationBenchmarks {

    @State(Scope.Thread)
    public static class SimpleAuthRequest extends PolicyTestState {

        final AuthorizationRequest basicRequest = AuthorizationRequest.builder()
                .operation("mqtt:publish")
                .resource("mqtt:topic:humidity")
                .sessionId("sessionId")
                .build();

        @Setup
        public void doSetup() throws ParseException, AuthorizationException {
            sessionManager.registerSession("sessionId", FakeSession.forDevice("MyThingName"));
            groupManager.setGroupConfiguration(GroupConfiguration.builder()
                    .definitions(Collections.singletonMap(
                            "group1", GroupDefinition.builder()
                                    .selectionRule("thingName: " + "MyThingName")
                                    .policyName("policy1")
                                    .build()))
                    .policies(Collections.singletonMap(
                            "policy1", Collections.singletonMap(
                                    "Statement1", AuthorizationPolicyStatement.builder()
                                                    .statementDescription("Policy description")
                                                    .effect(AuthorizationPolicyStatement.Effect.ALLOW)
                                                    .resources(new HashSet<>(Collections.singleton("mqtt:topic:humidity")))
                                                    .operations(new HashSet<>(Collections.singleton("mqtt:publish")))
                                                    .build())))
                    .build());
        }
    }

    @Benchmark
    public boolean GIVEN_single_group_permission_WHEN_simple_auth_request_THEN_successful_auth(SimpleAuthRequest state) throws Exception {
        return state.deviceAuthClient.canDevicePerform(state.basicRequest);
    }

    static abstract class PolicyTestState {
        final FakeSessionManager sessionManager = new FakeSessionManager();
        final GroupManager groupManager = new GroupManager();
        final DeviceAuthClient deviceAuthClient = new DeviceAuthClient(sessionManager, groupManager, null);
    }

    static class FakeSession implements Session {
        private final String thingName;
        private final boolean isComponent;

        static FakeSession forComponent() {
            return new FakeSession(null, true);
        }

        static FakeSession forDevice(String thingName) {
            return new FakeSession(thingName, false);
        }

        private FakeSession(String thingName, boolean isComponent) {
            this.thingName = thingName;
            this.isComponent = isComponent;
        }

        @Override
        public AttributeProvider getAttributeProvider(String attributeProviderNameSpace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DeviceAttribute getSessionAttribute(String ns, String name) {
            if ("Component".equalsIgnoreCase(ns) && name.equalsIgnoreCase("component")) {
                return isComponent ? new StringLiteralAttribute("component") : null;
            }
            if ("Thing".equalsIgnoreCase(ns) && name.equalsIgnoreCase("thingName")) {
                   return new WildcardSuffixAttribute(thingName);
            }
            throw new UnsupportedOperationException(String.format("Attribute %s.%s not supported", ns, name));
        }
    }

    private static class FakeSessionManager extends SessionManager {
        private final Map<String, Session> sessions = new HashMap<>();

        public FakeSessionManager() {
            super(null);
        }

        void registerSession(String id, Session session) {
            sessions.put(id, session);
        }

        @Override
        public Session findSession(String id) {
            return sessions.get(id);
        }
    }
}
