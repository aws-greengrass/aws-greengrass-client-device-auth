/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.deviceauth;

import com.aws.greengrass.clientdevices.auth.AuthorizationRequest;
import com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService;
import com.aws.greengrass.clientdevices.auth.DeviceAuthClient;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.configuration.GroupManager;
import com.aws.greengrass.clientdevices.auth.iot.*;
import com.aws.greengrass.clientdevices.auth.session.Session;
import com.aws.greengrass.clientdevices.auth.session.SessionImpl;
import com.aws.greengrass.clientdevices.auth.session.SessionManager;
import com.aws.greengrass.dependency.State;
import com.aws.greengrass.lifecyclemanager.GlobalStateChangeListener;
import com.aws.greengrass.lifecyclemanager.GreengrassService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.model.AuthorizeClientDeviceActionRequest;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@ExtendWith({GGExtension.class, UniqueRootPathExtension.class, MockitoExtension.class})
public class EvaluateClientDeviceActionsWithPolicyVariablesTest {

    @TempDir
    Path rootDir;
    private Kernel kernel;

    @Mock
    SessionManager sessionManager;

    private DeviceAuthClient deviceAuthClient;

    @BeforeEach
    void beforeEach(ExtensionContext context) {
        ignoreExceptionOfType(context, SpoolerStoreException.class);
        ignoreExceptionOfType(context, NoSuchFileException.class); // Loading CA keystore

        // Set this property for kernel to scan its own classpath to find plugins
        System.setProperty("aws.greengrass.scanSelfClasspath", "true");
        kernel = new Kernel();

        // Set up Iot auth client
        IotAuthClientFake iotAuthClientFake = new IotAuthClientFake();
        kernel.getContext().put(IotAuthClient.class, iotAuthClientFake);
    }

    private void startNucleusWithConfig(String configFileName) throws InterruptedException {
        startNucleusWithConfig(configFileName, State.RUNNING);
    }

    private void startNucleusWithConfig(String configFileName, State expectedServiceState) throws InterruptedException {
        CountDownLatch authServiceRunning = new CountDownLatch(1);
        kernel.parseArgs("-r", rootDir.toAbsolutePath().toString(), "-i",
                getClass().getResource(configFileName).toString());
        GlobalStateChangeListener listener = (GreengrassService service, State was, State newState) -> {
            if (service.getName().equals(ClientDevicesAuthService.CLIENT_DEVICES_AUTH_SERVICE_NAME)
                    && service.getState().equals(expectedServiceState)) {
                authServiceRunning.countDown();
            }
        };
        kernel.getContext().addGlobalStateChangeListener(listener);
        kernel.launch();
        assertThat(authServiceRunning.await(30L, TimeUnit.SECONDS), is(true));
        kernel.getContext().removeGlobalStateChangeListener(listener);
        startCDAWithConfig();
    }

    private void startCDAWithConfig() {
        // use DeviceAuthClient with mocked sessionManager
        GroupManager groupManager = kernel.getContext().get(GroupManager.class);
        CertificateStore certificateStore = kernel.getContext().get(CertificateStore.class);
        deviceAuthClient = new DeviceAuthClient(sessionManager, groupManager, certificateStore);
    }

    private Session createSession(String thingName) throws Exception {
        Certificate cert = CertificateFake.of("FAKE_CERT_ID");
        Thing thing = Thing.of(thingName);
        return new SessionImpl(cert, thing);
    }

    private AuthorizationRequest buildAuthZRequest(Session session, String operation, String topic) {
        AuthorizeClientDeviceActionRequest connectRequest =
                new AuthorizeClientDeviceActionRequest().withOperation(operation).withResource(String.format(topic, session.getSessionAttribute("Thing", "ThingName")));

        return AuthorizationRequest.builder().sessionId("sessionId")
                .operation(connectRequest.getOperation()).resource(connectRequest.getResource()).build();
    }

    @AfterEach
    void afterEach() {
        LogConfig.getRootLogConfig().reset();
        kernel.shutdown();
    }

    @Test
    void GIVEN_cdaWithDeviceGroupPolicy_WHEN_AuthorizeClientDeviceValidResourceAction_THEN_returnDecision() throws Exception {

        // CDA configuration with device group policies
        startNucleusWithConfig("policyVariableCda.yaml");

       when(sessionManager.findSession("sessionId")).thenReturn(createSession("myThing"));
        Session session = sessionManager.findSession("sessionId");

        // myThing should be able to connect to mqtt:myThing:foo
        AuthorizationRequest authzConnectReq = buildAuthZRequest(session, "mqtt:connect", "mqtt:%s:foo");

        assertThat(deviceAuthClient.canDevicePerform(authzConnectReq), is(true));

        // myThing should not be able to publish to mqtt:myThing:foo
        AuthorizationRequest authzPublishReq = buildAuthZRequest(session, "mqtt:publish", "mqtt:%s:foo");

        assertThat(deviceAuthClient.canDevicePerform(authzPublishReq), is(false));

        // myThing should be able to publish to mqtt:topic:myThing
        AuthorizationRequest authzTopicPublishReq = buildAuthZRequest(session, "mqtt:publish", "mqtt:topic:%s");

        assertThat(deviceAuthClient.canDevicePerform(authzTopicPublishReq), is(true));

        // myThing should not be able to connect to mqtt:topic:myThing
        AuthorizationRequest authzTopicConnectReq = buildAuthZRequest(session, "mqtt:connect", "mqtt:topic:%s");

        assertThat(deviceAuthClient.canDevicePerform(authzTopicConnectReq), is(false));

    }

    @Test
    void GIVEN_cdaWithDeviceGroupPolicy_WHEN_AuthorizeClientDeviceInvalidResourceAction_THEN_returnDecision() throws Exception {

        startNucleusWithConfig("policyVariableCda.yaml");

        when(sessionManager.findSession("sessionId")).thenReturn(createSession("myThing"));
        Session session = sessionManager.findSession("sessionId");

        // myThing should not be able to perform any action mqtt:myThing:bar
        AuthorizationRequest authzConnectReq = buildAuthZRequest(session, "mqtt:connect", "mqtt:%s:bar");

        assertThat(deviceAuthClient.canDevicePerform(authzConnectReq), is(false));

        AuthorizationRequest authzPublishReq = buildAuthZRequest(session, "mqtt:publish", "mqtt:%s:bar");

        assertThat(deviceAuthClient.canDevicePerform(authzPublishReq), is(false));
    }

    @Test
    void GIVEN_cdaWithDeviceGroupPolicy_WHEN_AuthorizeInvalidClientDeviceAction_THEN_returnTrue() throws Exception {

        startNucleusWithConfig("policyVariableCda.yaml");

        when(sessionManager.findSession("sessionId")).thenReturn(createSession("myFakeThing"));
        Session session = sessionManager.findSession("sessionId");

        // myFakeThing should not be able to connect to mqtt:myFakeThing:foo
        AuthorizationRequest authzConnectReq = buildAuthZRequest(session, "mqtt:connect", "mqtt:%s:foo");

        assertThat(deviceAuthClient.canDevicePerform(authzConnectReq), is(false));


        // myFakeThing should not be able to publish to mqtt:topic:myFakeThing
        AuthorizationRequest authzPublishReq = buildAuthZRequest(session, "mqtt:publish", "mqtt:topic:%s");

        assertThat(deviceAuthClient.canDevicePerform(authzPublishReq), is(false));
    }

}
