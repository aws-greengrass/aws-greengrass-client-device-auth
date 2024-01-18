/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.integrationtests.deviceauth;

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
import com.aws.greengrass.integrationtests.ipc.IPCTestUtils;
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
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.AuthorizeClientDeviceActionResponseHandler;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.model.AuthorizeClientDeviceActionRequest;
import software.amazon.awssdk.aws.greengrass.model.AuthorizeClientDeviceActionResponse;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

        // get token for client device to make ipc call
        ClientDevicesAuthServiceApi api = kernel.getContext().get(ClientDevicesAuthServiceApi.class);

        // create client device session and get token
        return api.getClientDeviceAuthToken("mqtt", new HashMap<String, String>() {{
            put("clientId", thingName);
            put("certificatePem", clientPem);
            put("username", "foo");
            put("password", "bar");
        }});
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
    }

    private static void authzClientDeviceAction(GreengrassCoreIPCClient ipcClient,
                                                AuthorizeClientDeviceActionRequest request,
                                                Boolean authorized)
            throws Exception {
        AuthorizeClientDeviceActionResponseHandler handler =
                ipcClient.authorizeClientDeviceAction(request, Optional.empty());
        AuthorizeClientDeviceActionResponse response = handler.getResponse().get(1, TimeUnit.SECONDS);
        assertThat(response.isIsAuthorized(), is(authorized));
    }

    @AfterEach
    void afterEach() {
        LogConfig.getRootLogConfig().reset();
        kernel.shutdown();
    }

    @Test
    void GIVEN_permissiveGroupPolicyWithThingNameVariable_WHEN_ClientAuthorizesWithThingNameValidResource_THEN_ClientAuthorized() throws Exception {
        // make IPC call
        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithAuthorizeClientDeviceActionPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);

            String deviceToken = getClientDeviceSessionAuthToken("myThing", clientPem);

            AuthorizeClientDeviceActionRequest connectRequest =
                    new AuthorizeClientDeviceActionRequest().withOperation("mqtt:connect").withResource("mqtt:myThing:foo")
             .withClientDeviceAuthToken(deviceToken);

            AuthorizeClientDeviceActionRequest publishRequest =
                    new AuthorizeClientDeviceActionRequest().withOperation("mqtt:publish").withResource("mqtt:topic:myThing")
                            .withClientDeviceAuthToken(deviceToken);

            authzClientDeviceAction(ipcClient, connectRequest, true);
            authzClientDeviceAction(ipcClient, publishRequest, true);
        }
    }

    @Test
    void
    GIVEN_permissiveGroupPolicyWithThingNameVariable_WHEN_ClientAuthorizesWithThingNameInvalidResource_THEN_ClientNotAuthorized() throws Exception {

        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithAuthorizeClientDeviceActionPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);

            String deviceToken = getClientDeviceSessionAuthToken("myThing", clientPem);

            AuthorizeClientDeviceActionRequest connectRequest =
                    new AuthorizeClientDeviceActionRequest().withOperation("mqtt:connect").withResource("mqtt:MyCoolThing:foo")
                            .withClientDeviceAuthToken(deviceToken);

            AuthorizeClientDeviceActionRequest publishRequest =
                    new AuthorizeClientDeviceActionRequest().withOperation("mqtt:publish").withResource("mqtt:topic:SomeThing")
                            .withClientDeviceAuthToken(deviceToken);

            authzClientDeviceAction(ipcClient, connectRequest, false);
            authzClientDeviceAction(ipcClient, publishRequest, false);
        }
    }

    @Test
    void GIVEN_permissiveGroupPolicyWithThingNameVariable_WHEN_ClientAuthorizesWithThingNameResourceInvalidAction_THEN_ClientNotAuthorized() throws Exception {

        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithAuthorizeClientDeviceActionPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);

            String deviceToken = getClientDeviceSessionAuthToken("myThing", clientPem);

            AuthorizeClientDeviceActionRequest connectRequest =
                    new AuthorizeClientDeviceActionRequest().withOperation("mqtt:connect").withResource("mqtt:topic:myThing")
                            .withClientDeviceAuthToken(deviceToken);

            AuthorizeClientDeviceActionRequest publishRequest =
                    new AuthorizeClientDeviceActionRequest().withOperation("mqtt:publish").withResource("mqtt:myThing:foo")
                            .withClientDeviceAuthToken(deviceToken);

            authzClientDeviceAction(ipcClient, connectRequest, false);
            authzClientDeviceAction(ipcClient, publishRequest, false);
        }
    }

    @Test
    void GIVEN_permissiveGroupPolicyWithThingNameVariable_WHEN_ClientAuthorizesWithInvalidThingNameResource_THEN_ClientNotAuthorized() throws Exception {

        try (EventStreamRPCConnection connection = IPCTestUtils.getEventStreamRpcConnection(kernel,
                "BrokerWithAuthorizeClientDeviceActionPermission")) {
            GreengrassCoreIPCClient ipcClient = new GreengrassCoreIPCClient(connection);

            String deviceToken = getClientDeviceSessionAuthToken("SomeThing", clientPem);

            AuthorizeClientDeviceActionRequest connectRequest =
                    new AuthorizeClientDeviceActionRequest().withOperation("mqtt:connect").withResource("mqtt:myThing:foo")
                            .withClientDeviceAuthToken(deviceToken);

            AuthorizeClientDeviceActionRequest publishRequest =
                    new AuthorizeClientDeviceActionRequest().withOperation("mqtt:publish").withResource("mqtt:topic:myThing")
                            .withClientDeviceAuthToken(deviceToken);

            authzClientDeviceAction(ipcClient, connectRequest, false);
            authzClientDeviceAction(ipcClient, publishRequest, false);
        }
    }
}
