/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.policy;

import com.aws.greengrass.clientdevices.auth.AuthorizationRequest;
import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.api.ClientDevicesAuthServiceApi;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.lifecyclemanager.Kernel;
import com.aws.greengrass.logging.impl.config.LogConfig;
import com.aws.greengrass.mqttclient.spool.SpoolerStoreException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.testcommons.testutilities.UniqueRootPathExtension;
import com.aws.greengrass.util.Pair;
import com.aws.greengrass.util.Utils;
import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static com.aws.greengrass.testcommons.testutilities.TestUtils.createServiceStateChangeWaiter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
public class PolicyTest {
    private final Map<String, Pair<Certificate, String>> clients = new HashMap<>();
    @TempDir
    Path rootDir;
    Kernel kernel;

    @BeforeEach
    void beforeEach(ExtensionContext context) {
        ignoreExceptionOfType(context, SpoolerStoreException.class);
        ignoreExceptionOfType(context, NoSuchFileException.class); // Loading CA keystore
    }

    @AfterEach
    void afterEach() {
        LogConfig.getRootLogConfig().reset();
        kernel.shutdown();
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

                Arguments.of("malformed-variable.yaml", Arrays.asList(
                        AuthZRequest.builder()
                                .thingName("myThing")
                                .operation("mqtt:publish")
                                .resource("mqtt:topic:foo")
                                .expectedResult(false)
                                .build()
                )),
                // TODO uncomment after validations have been added
//                Arguments.of("unknown-variable.yaml", Arrays.asList(
//                        AuthZRequest.builder()
//                                .thingName("myThing")
//                                .operation("mqtt:publish")
//                                .resource("mqtt:topic:foo")
//                                .expectedResult(false)
//                                .build()
//                )),

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
                ))
        );
    }

    @ParameterizedTest
    @MethodSource("authzRequests")
    void GIVEN_cda_with_policy_configuration_WHEN_client_requests_authorization_THEN_client_is_authorized(String configFile, List<AuthZRequest> requests) throws Exception {
        startNucleus(configFile);

        for (AuthZRequest request : requests) {
            boolean actualResult = api().authorizeClientDeviceAction(AuthorizationRequest.builder()
                    .sessionId(generateAuthToken(request.getThingName()))
                    .operation(request.getOperation())
                    .resource(request.getResource())
                    .build());
            assertThat(String.format("Unexpected authZ result. expectedResult=%b, request=%s",
                            request.isExpectedResult(), request),
                    actualResult, is(request.isExpectedResult()));
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private String generateAuthToken(String thingName) throws Exception {
        Pair<Certificate, String> clientCert = clients.computeIfAbsent(thingName, k -> {
            try {
                Pair<Certificate, String> cert = generateClientCert();

                // register client within CDA
                ThingRegistry thingRegistry = kernel.getContext().get(ThingRegistry.class);
                Thing thing = thingRegistry.createThing(thingName);
                thing.attachCertificate(cert.getLeft().getCertificateId());
                thingRegistry.updateThing(thing);

                return cert;
            } catch (Exception e) {
                fail(e);
                return null;
            }
        });

        return api().getClientDeviceAuthToken("mqtt", Utils.immutableMap(
            "clientId", thingName,
            "certificatePem", clientCert.getRight()
        ));
    }

    private Pair<Certificate, String> generateClientCert() throws Exception {
        // create certificate to attach to thing
        List<X509Certificate> clientCertificates = CertificateTestHelpers.createClientCertificates(1);
        String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
        CertificateRegistry certificateRegistry = kernel.getContext().get(CertificateRegistry.class);
        Certificate cert = certificateRegistry.getOrCreateCertificate(clientPem);
        cert.setStatus(Certificate.Status.ACTIVE);
        // activate certificate
        certificateRegistry.updateCertificate(cert);
        return new Pair<>(cert, clientPem);
    }

    private ClientDevicesAuthServiceApi api() {
        return kernel.getContext().get(ClientDevicesAuthServiceApi.class);
    }

    private void startNucleus(String configFileName) {
        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();
        kernel.getContext().put(IotAuthClient.class, new IotAuthClientFake());
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        Runnable mainRunning = createServiceStateChangeWaiter(kernel,
                ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME, 30, State.RUNNING);
        kernel.launch();
        mainRunning.run();
    }
}
