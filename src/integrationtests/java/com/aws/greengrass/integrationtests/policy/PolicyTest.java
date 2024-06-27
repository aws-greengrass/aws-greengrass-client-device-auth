/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.policy;

import com.aws.greengrass.clientdevices.auth.AuthorizationRequest;
import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.configuration.AuthorizationPolicyStatement;
import com.aws.greengrass.clientdevices.auth.configuration.GroupConfiguration;
import com.aws.greengrass.clientdevices.auth.configuration.GroupDefinition;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.exception.PolicyException;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.clientdevices.auth.iot.IotCoreClient;
import com.aws.greengrass.clientdevices.auth.iot.IotCoreClientFake;
import com.aws.greengrass.clientdevices.auth.iot.NetworkStateFake;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.impl.config.LogConfig;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.UniqueRootPathExtension;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.utils.ImmutableMap;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.DEVICE_GROUPS_TOPICS;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static com.aws.greengrass.testcommons.testutilities.TestUtils.createServiceStateChangeWaiter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
public class PolicyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, String> clients = new HashMap<>();

    private final NetworkStateFake networkStateProvider = new NetworkStateFake();
    private final IotAuthClientFake iotAuthClient = new IotAuthClientFake();
    private final IotCoreClientFake iotCoreClient = new IotCoreClientFake();
    private final Map<String, String> DEFAULT_THING_ATTRIBUTES = ImmutableMap.of("myAttribute", "attribute");

    @TempDir
    Path rootDir;
    Kernel kernel;

    @BeforeEach
    void beforeEach(ExtensionContext context) {
        ignoreExceptionOfType(context, SpoolerStoreException.class);
        ignoreExceptionOfType(context, NoSuchFileException.class); // Loading CA keystore
        iotCoreClient.setThingAttributes(DEFAULT_THING_ATTRIBUTES);
        networkStateProvider.goOnline();
    }

    @AfterEach
    void afterEach() {
        LogConfig.getRootLogConfig().reset();
        kernel.shutdown();
    }

    @Test
    void GIVEN_cda_with_client_policy_WHEN_resource_removed_from_policy_THEN_resource_not_accessible_anymore() throws Exception {
        startNucleus("empty-config.yaml");

        // set policy that allows publish on topic "a"
        replacePolicy(GroupConfiguration.builder()
                .definitions(Utils.immutableMap("group1", GroupDefinition.builder()
                        .policyName("policyA")
                        .selectionRule("thingName: myThing")
                        .build()))
                .policies(Utils.immutableMap("policyA", Utils.immutableMap("statement1", AuthorizationPolicyStatement.builder()
                        .statementDescription("allow publish on topic a")
                        .operations(Stream.of("mqtt:publish").collect(Collectors.toSet()))
                        .resources(Stream.of("mqtt:topic:a").collect(Collectors.toSet()))
                        .effect(AuthorizationPolicyStatement.Effect.ALLOW)
                        .build())))
                .build());

        // verify we can publish to topic "a"
        assertTrue(api().authorizeClientDeviceAction(AuthorizationRequest.builder()
                .sessionId(generateAuthToken("myThing"))
                .operation("mqtt:publish")
                .resource("mqtt:topic:a")
                .build()));
        // verify we cannot publish to topic "b"
        assertFalse(api().authorizeClientDeviceAction(AuthorizationRequest.builder()
                .sessionId(generateAuthToken("myThing"))
                .operation("mqtt:publish")
                .resource("mqtt:topic:b")
                .build()));

        // replace topic "a" from the policy with topic "b"
        replacePolicy(GroupConfiguration.builder()
                .definitions(Utils.immutableMap("group1", GroupDefinition.builder()
                                .policyName("policyA")
                                .selectionRule("thingName: myThing")
                        .build()))
                .policies(Utils.immutableMap("policyA", Utils.immutableMap("statement1", AuthorizationPolicyStatement.builder()
                                .statementDescription("allow publish on topic b")
                                .operations(Stream.of("mqtt:publish").collect(Collectors.toSet()))
                                .resources(Stream.of("mqtt:topic:b").collect(Collectors.toSet()))
                                .effect(AuthorizationPolicyStatement.Effect.ALLOW)
                        .build())))
                .build());

        // verify we can no longer publish to topic "a"
        assertFalse(api().authorizeClientDeviceAction(AuthorizationRequest.builder()
                .sessionId(generateAuthToken("myThing"))
                .operation("mqtt:publish")
                .resource("mqtt:topic:a")
                .build()));
        // verify we can publish to topic "b"
        assertTrue(api().authorizeClientDeviceAction(AuthorizationRequest.builder()
                .sessionId(generateAuthToken("myThing"))
                .operation("mqtt:publish")
                .resource("mqtt:topic:b")
                .build()));
    }

    @Test
    void GIVEN_cda_running_WHEN_policy_updated_with_invalid_policy_variable_THEN_cda_broken(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, PolicyException.class);
        startNucleus("empty-config.yaml");

        Runnable waitForBroken = createServiceStateChangeWaiter(kernel,
                ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME, 30, State.BROKEN);

        // merge bad policy config
        replacePolicy(GroupConfiguration.builder()
                .definitions(Utils.immutableMap("group1", GroupDefinition.builder()
                        .policyName("policyA")
                        .selectionRule("thingName: myThing")
                        .build()))
                .policies(Utils.immutableMap("policyA", Utils.immutableMap("statement1", AuthorizationPolicyStatement.builder()
                        .statementDescription("invalid policy variable")
                        .operations(Stream.of("mqtt:publish").collect(Collectors.toSet()))
                        .resources(Stream.of("mqtt:topic:${iot:Connection.Thing.Unknown}").collect(Collectors.toSet()))
                        .effect(AuthorizationPolicyStatement.Effect.ALLOW)
                        .build())))
                .build());
        waitForBroken.run();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "malformed-variable.yaml",
            "unknown-variable.yaml"
    })
    void GIVEN_invalid_cda_policy_WHEN_cda_startups_THEN_cda_broken(String configFile, ExtensionContext context) {
        ignoreExceptionOfType(context, PolicyException.class);
        startNucleus(configFile, State.BROKEN);
    }

    @Value
    @Builder
    static class AuthZRequest {
        String thingName;
        String operation;
        String resource;
        boolean expectedResult;
    }

    public static Stream<Arguments> authzRequests() {
        return Stream.of( // config file, thing name, operation, resource, expected result
                Arguments.of("variable-in-resource-type.yaml", Arrays.asList(
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:myThing:foo")
                                .expectedResult(true)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:myThing")
                                .expectedResult(false)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:MyCoolThing:foo")
                                .expectedResult(false)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("SomeThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:myThing:foo")
                                .expectedResult(false)
                                .build()
                )),

                Arguments.of("variable-in-resource-name.yaml", Arrays.asList(
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:myThing")
                                .expectedResult(true)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:SomeThing")
                                .expectedResult(false)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:myThing:foo")
                                .expectedResult(false)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("SomeThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:myThing")
                                .expectedResult(false)
                                .build()
                )),

                Arguments.of("variables-in-resource-name.yaml", Arrays.asList(
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:myThing/myThing")
                                .expectedResult(true)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:myThing2/myThing")
                                .expectedResult(false)
                                .build()
                )),

                Arguments.of("wildcards-in-resource-type.yaml", Arrays.asList(
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:hello/myThing/world")
                                .expectedResult(true)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:hello/myThing")
                                .expectedResult(false)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:myThing/world")
                                .expectedResult(false)
                                .build()
                )),

                Arguments.of("variables-and-wildcards.yaml", Arrays.asList(
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:myThing/hello")
                                .expectedResult(true)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:myThing/myThing")
                                .expectedResult(true)
                                .build(),
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:hello/myThing")
                                .expectedResult(false)
                                .build()
                )),
                Arguments.of("thing-attribute-variable.yaml", Arrays.asList(
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:attribute")
                                .expectedResult(true)
                                .build()
                ))
        );
    }

    @ParameterizedTest
    @MethodSource("authzRequests")
    void GIVEN_cda_with_policy_configuration_WHEN_client_requests_authorization_THEN_client_is_authorized(String configFile, List<AuthZRequest> requests) throws Exception {
        // register certificates and associate client devices with core BEFORE starting CDA.
        // CDA needs this data on startup when:
        //   1) the policy has a thing attr variable (see thing-attribute-variable.yaml)
        Map<String, String> authTokens = requests.stream()
                .map(AuthZRequest::getThingName)
                .distinct()
                .collect(Collectors.toMap(thingName -> thingName, this::createOrGetClient));

        startNucleus(configFile);

        for (AuthZRequest request : requests) {
            boolean actualResult = api().authorizeClientDeviceAction(AuthorizationRequest.builder()
                    .sessionId(generateAuthToken(request.getThingName(), authTokens.get(request.getThingName())))
                    .operation(request.getOperation())
                    .resource(request.getResource())
                    .build());
            assertThat(String.format("Unexpected authZ result. expectedResult=%b, request=%s",
                            request.isExpectedResult(), request),
                    actualResult, is(request.isExpectedResult()));
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private String createOrGetClient(String thingName) {
        return clients.computeIfAbsent(thingName, k -> {
            try {
                String cert = generateClientCert();
                iotAuthClient.activateCert(cert);
                iotAuthClient.attachCertificateToThing(thingName, cert);
                iotAuthClient.attachThingToCore(() -> thingName);
                return cert;
            } catch (Exception e) {
                fail(e);
                return null;
            }
        });
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private String generateAuthToken(String thingName) {
        return generateAuthToken(thingName, createOrGetClient(thingName));
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private String generateAuthToken(String thingName, String cert) {
        try {
            assertTrue(api().verifyClientDeviceIdentity(cert)); // add cert to CDA cert registry
            return api().getClientDeviceAuthToken("mqtt", Utils.immutableMap(
                    "clientId", thingName,
                    "certificatePem", cert
            ));
        } catch (AuthenticationException e) {
            fail(e);
            return null;
        }
    }

    private String generateClientCert() throws Exception {
        List<X509Certificate> clientCertificates = CertificateTestHelpers.createClientCertificates(1);
        return CertificateHelper.toPem(clientCertificates.get(0));
    }

    @SuppressWarnings("unchecked")
    private void replacePolicy(GroupConfiguration groupConfiguration) {
        kernel.findServiceTopic(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME)
                .lookupTopics("configuration", DEVICE_GROUPS_TOPICS)
                .replaceAndWait(MAPPER.convertValue(groupConfiguration, Map.class));
    }

    private ClientDevicesAuthServiceApi api() {
        return kernel.getContext().get(ClientDevicesAuthServiceApi.class);
    }

    private void startNucleus(String configFileName) {
        startNucleus(configFileName, State.RUNNING);
    }

    private void startNucleus(String configFileName, State expectedState) {
        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        kernel.getContext().put(IotAuthClient.class, iotAuthClient);
        kernel.getContext().put(IotCoreClient.class, iotCoreClient);
        kernel.getContext().put(NetworkStateProvider.class, networkStateProvider);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        Runnable mainRunning = createServiceStateChangeWaiter(kernel,
                ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME, 30, expectedState);
        kernel.launch();
        mainRunning.run();
    }
}
