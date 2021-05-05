/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.certificatemanager.CertificateManager;
import com.aws.greengrass.certificatemanager.certificate.CertificateHelper;
import com.aws.greengrass.certificatemanager.certificate.CertificateRequestGenerator;
import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.configuration.Permission;
import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.iot.Thing;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.Digest;
import org.hamcrest.collection.IsMapContaining;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class DeviceAuthClientTest {

    @InjectMocks
    private DeviceAuthClient authClient;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private GroupManager groupManager;

    @Mock
    private IotAuthClient iotClient;

    @Mock
    private CertificateStore certificateStore;

    @TempDir
    Path tempDir;

    @Test
    void GIVEN_emptySessionManager_WHEN_createSession_THEN_sessionReturned() throws Exception {
        String certificatePem = "FAKE_PEM";
        DeviceAuthClient deviceAuthClient =
                new DeviceAuthClient(new SessionManager(), groupManager, iotClient, certificateStore);
        when(iotClient.getActiveCertificateId(certificatePem)).thenReturn(Optional.of("certificateId"));
        String sessionId = deviceAuthClient.createSession(certificatePem);
        assertThat(sessionId, not(emptyOrNullString()));
        String certificateHash = Digest.calculateWithUrlEncoderNoPadding(certificatePem);
        verify(certificateStore).storeDeviceCertificateIfNotPresent(certificateHash, certificatePem);
    }

    @Test
    void GIVEN_noActiveCertificateId_WHEN_createSession_THEN_throwAuthenticationException() {
        String certificatePem = "FAKE_PEM";
        DeviceAuthClient deviceAuthClient =
                new DeviceAuthClient(new SessionManager(), groupManager, iotClient, certificateStore);
        when(iotClient.getActiveCertificateId(certificatePem)).thenReturn(Optional.empty());

        assertThrows(AuthenticationException.class, () -> deviceAuthClient.createSession(certificatePem));
    }

    @Test
    void GIVEN_getActiveCertificateIdThrowException_WHEN_createSession_THEN_throwAuthenticationException() {
        String certificatePem = "FAKE_PEM";
        DeviceAuthClient deviceAuthClient =
                new DeviceAuthClient(new SessionManager(), groupManager, iotClient, certificateStore);
        when(iotClient.getActiveCertificateId(certificatePem)).thenThrow(CloudServiceInteractionException.class);

        assertThrows(AuthenticationException.class, () -> deviceAuthClient.createSession(certificatePem));
    }

    @Test
    void GIVEN_storeCertificateThrowException_WHEN_createSession_THEN_throwAuthenticationException(
            ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, IOException.class);
        String certificatePem = "FAKE_PEM";
        DeviceAuthClient deviceAuthClient =
                new DeviceAuthClient(new SessionManager(), groupManager, iotClient, certificateStore);
        when(iotClient.getActiveCertificateId(certificatePem)).thenReturn(Optional.of("certificateId"));
        doThrow(IOException.class).when(certificateStore).storeDeviceCertificateIfNotPresent(any(), any());

        Exception e =
                assertThrows(AuthenticationException.class, () -> deviceAuthClient.createSession(certificatePem));
        assertThat(e.getMessage(), containsString("Failed to store"));
    }

    @Test
    void GIVEN_thingAssociatedWithCertificate_WHEN_attachThing_THEN_thingAddedToSession() throws Exception {
        String sessionId = "sessionId";
        String thingName = "thingName";
        Session session = new Session(new Certificate("certificatePemHash", "certificateId"));
        when(sessionManager.findSession(sessionId)).thenReturn(session);
        when(iotClient.isThingAttachedToCertificate(any(), any())).thenReturn(true);

        authClient.attachThing(sessionId, thingName);

        assertThat(session.getSessionAttribute(Thing.NAMESPACE, "ThingName").toString(), is(thingName));
        ArgumentCaptor<Thing> thingArgumentCaptor = ArgumentCaptor.forClass(Thing.class);
        ArgumentCaptor<Certificate> certificateArgumentCaptor = ArgumentCaptor.forClass(Certificate.class);
        verify(iotClient)
                .isThingAttachedToCertificate(thingArgumentCaptor.capture(), certificateArgumentCaptor.capture());
        assertThat(thingArgumentCaptor.getValue().getThingName(), is(thingName));
        assertThat(certificateArgumentCaptor.getValue().getIotCertificateId(), is("certificateId"));
    }

    @Test
    void GIVEN_verifyThingAssociationException_WHEN_attachThing_THEN_throwsAuthenticationException() {
        String sessionId = "sessionId";
        String thingName = "thingName";
        Session session = new Session(new Certificate("certificatePemHash", "certificateId"));
        when(sessionManager.findSession(sessionId)).thenReturn(session);
        when(iotClient.isThingAttachedToCertificate(any(), any())).thenThrow(CloudServiceInteractionException.class);

        assertThrows(AuthenticationException.class, () -> authClient.attachThing(sessionId, thingName));

        assertThat(session.getSessionAttribute(Thing.NAMESPACE, "ThingName"), nullValue());
    }

    @Test
    void GIVEN_invalidSessionId_WHEN_canDevicePerform_THEN_authorizationExceptionThrown() {
        DeviceAuthClient deviceAuthClient =
                new DeviceAuthClient(new SessionManager(), new GroupManager(), iotClient, certificateStore);
        String sessionId = "FAKE_SESSION";
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest("mqtt:connect", "mqtt:clientId:clientId", sessionId, "clientId");
        assertThrows(AuthorizationException.class, () -> deviceAuthClient.canDevicePerform(authorizationRequest));
    }

    @Test
    void GIVEN_missingDevicePermission_WHEN_canDevicePerform_THEN_authorizationReturnFalse() throws Exception {
        String certificatePem = "FAKE_PEM";
        DeviceAuthClient deviceAuthClient =
                new DeviceAuthClient(new SessionManager(), new GroupManager(), iotClient, certificateStore);
        when(iotClient.getActiveCertificateId(certificatePem)).thenReturn(Optional.of("certificateId"));
        String sessionId = deviceAuthClient.createSession(certificatePem);
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest("mqtt:connect", "mqtt:clientId:clientId", sessionId, "clientId");
        assertThat(deviceAuthClient.canDevicePerform(authorizationRequest), is(false));
    }

    @Test
    void GIVEN_session_no_thing_WHEN_receive_authorization_request_THEN_thing_added_to_session() throws Exception {
        Session session = new Session(new Certificate("certificatePemHash", "certificateId"));
        when(sessionManager.findSession("sessionId")).thenReturn(session);
        when(iotClient.isThingAttachedToCertificate(any(), any())).thenReturn(true);
        when(groupManager.getApplicablePolicyPermissions(session)).thenReturn(Collections.singletonMap("group1",
                Collections.singleton(
                        Permission.builder().operation("mqtt:publish").resource("mqtt:topic:foo").principal("group1")
                                .build())));

        boolean authorized = authClient.canDevicePerform(constructAuthorizationRequest());

        assertThat(authorized, is(true));
        assertThat(session, IsMapContaining.hasKey("Thing"));
        assertThat(session.getSessionAttribute("Thing", "ThingName").toString(), is("clientId"));
        ArgumentCaptor<Thing> thingArgumentCaptor = ArgumentCaptor.forClass(Thing.class);
        ArgumentCaptor<Certificate> certificateArgumentCaptor = ArgumentCaptor.forClass(Certificate.class);
        verify(iotClient)
                .isThingAttachedToCertificate(thingArgumentCaptor.capture(), certificateArgumentCaptor.capture());
        assertThat(thingArgumentCaptor.getValue().getThingName(), is("clientId"));
        assertThat(certificateArgumentCaptor.getValue().getCertificateHash(), is("certificatePemHash"));
    }

    @Test
    void GIVEN_session_has_thing_WHEN_receive_authorization_request_THEN_thing_in_session_not_changed()
            throws Exception {
        Session session = new Session(new Certificate("certificatePemHash", "certificateId"));
        session.put(Thing.NAMESPACE, new Thing("baz"));
        when(sessionManager.findSession("sessionId")).thenReturn(session);
        when(groupManager.getApplicablePolicyPermissions(session)).thenReturn(Collections.singletonMap("group1",
                Collections.singleton(
                        Permission.builder().operation("mqtt:publish").resource("mqtt:topic:bar").principal("group1")
                                .build())));

        boolean authorized = authClient.canDevicePerform(constructAuthorizationRequest());

        assertThat(authorized, is(false));
        assertThat(session, IsMapContaining.hasKey("Thing"));
        assertThat(session.getSessionAttribute("Thing", "ThingName").toString(), is("baz"));
        verify(iotClient, never()).isThingAttachedToCertificate(any(), any());
    }

    private AuthorizationRequest constructAuthorizationRequest() {
        return AuthorizationRequest.builder().sessionId("sessionId").clientId("clientId").operation("mqtt:publish")
                .resource("mqtt:topic:foo").build();
    }

    @Test
    void GIVEN_session_id_WHEN_close_session_THEN_invoke_session_manager_close_session() throws Exception {
        authClient.closeSession("id");

        verify(sessionManager).closeSession("id");
    }

    @Test
    void GIVEN_greengrassComponentCertChainPem_WHEN_createSession_THEN_allowAllSessionIdReturned() throws Exception {
        CertificateStore certificateStore = new CertificateStore(tempDir);
        certificateStore.update("password", CertificateStore.CAType.RSA_2048);
        CertificateManager certificateManager = new CertificateManager(certificateStore);
        KeyPair clientKeyPair = CertificateStore.newRSAKeyPair();
        String csr = CertificateRequestGenerator.createCSR(clientKeyPair, "Thing", null, null);

        AtomicReference<X509Certificate[]> clientCertChain = new AtomicReference<>();
        Consumer<X509Certificate[]> cb = t -> {
            clientCertChain.set(t);
        };
        certificateManager.subscribeToClientCertificateUpdates(csr, cb);

        authClient = new DeviceAuthClient(sessionManager, groupManager, iotClient, certificateStore);

        String certificatePem =
                CertificateHelper.toPem(clientCertChain.get()[0]) + CertificateHelper.toPem(clientCertChain.get()[1]);
        assertThat(authClient.createSession(certificatePem), is("ALLOW_ALL"));

        AuthorizationRequest authorizationRequest =
                AuthorizationRequest.builder().sessionId("ALLOW_ALL").clientId("clientId").operation("mqtt:publish")
                        .resource("mqtt:topic:foo").build();
        assertThat(authClient.canDevicePerform(authorizationRequest), is(true));
    }
}
