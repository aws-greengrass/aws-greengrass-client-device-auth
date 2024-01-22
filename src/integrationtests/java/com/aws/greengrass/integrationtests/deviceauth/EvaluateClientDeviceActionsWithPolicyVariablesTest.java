/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.deviceauth;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static com.aws.greengrass.testcommons.testutilities.TestUtils.createServiceStateChangeWaiter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
public class EvaluateClientDeviceActionsWithPolicyVariablesTest {
    @TempDir
    Path rootDir;
    private Kernel kernel;

    private Certificate certificate;

    private String clientPem;
    private ClientDevicesAuthServiceApi api;

    @BeforeEach
    void beforeEach(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, SpoolerStoreException.class);
        ignoreExceptionOfType(context, NoSuchFileException.class); // Loading CA keystore

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();

        // Set up Iot auth client
        IotAuthClientFake iotAuthClientFake = new IotAuthClientFake();
        kernel.getContext().put(IotAuthClient.class, iotAuthClientFake);

        // start CDA service with configuration
        startNucleusWithConfig("config.yaml");

        // create certificate that client devices can use
        setClientDeviceCertificatePem();

        this.api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);
    }

    private void setClientDeviceCertificatePem() throws Exception{
        // create certificate to attach to thing
        List<X509Certificate> clientCertificates = CertificateTestHelpers.createClientCertificates(1);
        String clientPem = CertificateHelper.toPem(clientCertificates.get(0));
        CertificateRegistry certificateRegistry = kernel.getContext().get(CertificateRegistry.class);
        Certificate cert = certificateRegistry.getOrCreateCertificate(clientPem);
        cert.setStatus(Certificate.Status.ACTIVE);

        // activate certificate
        certificateRegistry.updateCertificate(cert);
        this.certificate = cert;
        this.clientPem = clientPem;
    }

    private String getClientDeviceSessionAuthToken(String thingName, String clientPem) throws Exception {
        // create thing - needed for api call to validate thing + certificate
        ThingRegistry thingRegistry = kernel.getContext().get(ThingRegistry.class);
        Thing MyThing = thingRegistry.createThing(thingName);
        MyThing.attachCertificate(certificate.getCertificateId());
        thingRegistry.updateThing(MyThing);

        // create client device session and get token
        return api.getClientDeviceAuthToken("mqtt", new HashMap<String, String>() {{
            put("clientId", thingName);
            put("certificatePem", clientPem);
            put("username", "foo");
            put("password", "bar");
        }});
    }

    private void startNucleusWithConfig(String configFileName) {
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        Runnable mainRunning = createServiceStateChangeWaiter(kernel,
                ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME, 30, State.RUNNING);
        kernel.launch();
        mainRunning.run();
    }

    private void authzClientDeviceAction(AuthorizationRequest request, Boolean authorized) throws Exception {
        assertThat(api.authorizeClientDeviceAction(request), is(authorized));
    }

    @AfterEach
    void afterEach() {
        LogConfig.getRootLogConfig().reset();
        kernel.shutdown();
    }

    private static Stream<Arguments> authzRequests () {
        return Stream.of(
                // GIVEN_permissiveGroupPolicyWithThingNameVariable_WHEN_ClientAuthorizesWithThingNameValidResource_THEN_ClientAuthorized
                Arguments.of("myThing", "mqtt:connect", "mqtt:myThing:foo", true),
                Arguments.of("myThing", "mqtt:publish", "mqtt:topic:myThing", true),
                // GIVEN_permissiveGroupPolicyWithThingNameVariable_WHEN_ClientAuthorizesWithThingNameInvalidResource_THEN_ClientNotAuthorized
                Arguments.of("myThing", "mqtt:connect", "mqtt:MyCoolThing:foo", false),
                Arguments.of("myThing", "mqtt:publish", "mqtt:topic:SomeThing", false),
                // GIVEN_permissiveGroupPolicyWithThingNameVariable_WHEN_ClientAuthorizesWithThingNameResourceInvalidAction_THEN_ClientNotAuthorized
                Arguments.of("myThing", "mqtt:connect", "mqtt:topic:myThing", false),
                Arguments.of("myThing", "mqtt:publish", "mqtt:myThing:foo", false),
                // GIVEN_permissiveGroupPolicyWithThingNameVariable_WHEN_ClientAuthorizesWithInvalidThingNameResource_THEN_ClientNotAuthorized
                Arguments.of("SomeThing", "mqtt:connect", "mqtt:myThing:foo", false),
                Arguments.of("SomeThing", "mqtt:publish", "mqtt:topic:myThing", false)
        );
    }

    @ParameterizedTest
    @MethodSource("authzRequests")
    void GIVEN_permissiveGroupPolicyWithThingNameVariable_WHEN_ClientAuthorizesWithThingName_THEN_ClientAuthorized(
            String thingName, String operation, String resource, Boolean result) throws Exception {
        String deviceToken = getClientDeviceSessionAuthToken(thingName, clientPem);

        AuthorizationRequest request = AuthorizationRequest.builder().sessionId(deviceToken)
                .operation(operation).resource(resource).build();

        authzClientDeviceAction(request, result);
    }
}
