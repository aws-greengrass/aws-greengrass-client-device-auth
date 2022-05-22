/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;

import com.aws.greengrass.certificatemanager.CertificateManager;
import com.aws.greengrass.certificatemanager.certificate.CertificateExpiryMonitor;
import com.aws.greengrass.certificatemanager.certificate.CISShadowMonitor;
import com.aws.greengrass.certificatemanager.certificate.CertificateHelper;
import com.aws.greengrass.certificatemanager.certificate.CertificateRequestGenerator;
import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.certificatemanager.certificate.CertificatesConfig;
import com.aws.greengrass.cisclient.ConnectivityInfoProvider;
import com.aws.greengrass.componentmanager.KernelConfigResolver;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.device.configuration.GroupManager;
import com.aws.greengrass.device.configuration.Permission;
import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.Component;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.iot.Thing;
import com.aws.greengrass.device.session.Session;
import com.aws.greengrass.device.session.SessionImpl;
import com.aws.greengrass.device.session.SessionManager;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    @SuppressWarnings("PMD.UnusedPrivateField") // Required for injecting into DeviceAuthClient
    private CertificateStore certificateStore;

    @Mock
    private ConnectivityInfoProvider mockConnectivityInfoProvider;

    @Mock
    CertificateExpiryMonitor mockCertExpiryMonitor;

    @Mock
    CISShadowMonitor mockShadowMonitor;

    @TempDir
    Path tempDir;

    private Topics configurationTopics;

    @BeforeEach
    void beforeEach() {
        configurationTopics = Topics.of(new Context(), KernelConfigResolver.CONFIGURATION_CONFIG_KEY, null);
    }

    @AfterEach
    void afterEach() throws IOException {
        configurationTopics.getContext().close();
    }

    @Test
    void GIVEN_emptySessionManager_WHEN_createSession_THEN_sessionReturned() throws Exception {
        String certificatePem = "FAKE_PEM";
        when(iotClient.getActiveCertificateId(certificatePem)).thenReturn(Optional.of("certificateId"));
        when(sessionManager.createSession(any())).thenReturn("sessionId");
        String sessionId = authClient.createSession(certificatePem);
        assertThat(sessionId, is("sessionId"));
        ArgumentCaptor<Certificate> argumentCaptor = ArgumentCaptor.forClass(Certificate.class);
        verify(sessionManager).createSession(argumentCaptor.capture());
        Certificate certificate = argumentCaptor.getValue();
        assertThat(certificate.getIotCertificateId(), is("certificateId"));
    }

    @Test
    void GIVEN_noActiveCertificateId_WHEN_createSession_THEN_throwAuthenticationException() {
        String certificatePem = "FAKE_PEM";
        when(iotClient.getActiveCertificateId(certificatePem)).thenReturn(Optional.empty());

        assertThrows(AuthenticationException.class, () -> authClient.createSession(certificatePem));
    }

    @Test
    void GIVEN_getActiveCertificateIdThrowException_WHEN_createSession_THEN_throwAuthenticationException() {
        String certificatePem = "FAKE_PEM";
        when(iotClient.getActiveCertificateId(certificatePem)).thenThrow(CloudServiceInteractionException.class);

        assertThrows(AuthenticationException.class, () -> authClient.createSession(certificatePem));
    }

    @Test
    void GIVEN_thingAssociatedWithCertificate_WHEN_attachThing_THEN_thingAddedToSession() throws Exception {
        String sessionId = "sessionId";
        String thingName = "thingName";
        Session session = new SessionImpl(new Certificate("certificateId"));
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
        Session session = new SessionImpl(new Certificate("certificateId"));
        when(sessionManager.findSession(sessionId)).thenReturn(session);
        when(iotClient.isThingAttachedToCertificate(any(), any())).thenThrow(CloudServiceInteractionException.class);

        assertThrows(AuthenticationException.class, () -> authClient.attachThing(sessionId, thingName));

        assertThat(session.getSessionAttribute(Thing.NAMESPACE, "ThingName"), nullValue());
    }

    @Test
    void GIVEN_invalidSessionId_WHEN_canDevicePerform_THEN_authorizationExceptionThrown() {
        String sessionId = "FAKE_SESSION";
        when(sessionManager.findSession(sessionId)).thenReturn(null);
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest("mqtt:connect", "mqtt:clientId:clientId", sessionId);
        assertThrows(AuthorizationException.class, () -> authClient.canDevicePerform(authorizationRequest));
    }

    @Test
    void GIVEN_missingDevicePermission_WHEN_canDevicePerform_THEN_authorizationReturnFalse() throws Exception {
        String sessionId = "FAKE_SESSION";
        Session session = new SessionImpl(new Certificate("certificateId"));
        when(sessionManager.findSession(sessionId)).thenReturn(session);
        AuthorizationRequest authorizationRequest =
                new AuthorizationRequest("mqtt:connect", "mqtt:clientId:clientId", sessionId);
        assertThat(authClient.canDevicePerform(authorizationRequest), is(false));
    }

    @Test
    void GIVEN_sessionHasPermission_WHEN_canDevicePerform_THEN_authorizationReturnTrue() throws Exception {
        Session session = new SessionImpl(new Certificate("certificateId"));
        when(sessionManager.findSession("sessionId")).thenReturn(session);
        when(groupManager.getApplicablePolicyPermissions(session)).thenReturn(Collections.singletonMap("group1",
                Collections.singleton(
                        Permission.builder().operation("mqtt:publish").resource("mqtt:topic:foo").principal("group1")
                                .build())));

        boolean authorized = authClient.canDevicePerform(constructAuthorizationRequest());

        assertThat(authorized, is(true));
        verify(iotClient, never()).isThingAttachedToCertificate(any(), any());
    }

    @Test
    void GIVEN_internalClientSession_WHEN_canDevicePerform_THEN_authorizationReturnTrue() throws Exception {
        Session session = new SessionImpl(new Certificate("certificateId"));
        session.putAttributeProvider(Component.NAMESPACE, new Component());
        when(sessionManager.findSession("sessionId")).thenReturn(session);

        boolean authorized = authClient.canDevicePerform(constructAuthorizationRequest());

        assertThat(authorized, is(true));
        verify(iotClient, never()).isThingAttachedToCertificate(any(), any());
    }

    private AuthorizationRequest constructAuthorizationRequest() {
        return AuthorizationRequest.builder().sessionId("sessionId").operation("mqtt:publish")
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
        CertificateManager certificateManager = new CertificateManager(certificateStore, mockConnectivityInfoProvider,
                mockCertExpiryMonitor, mockShadowMonitor, Clock.systemUTC());
        certificateManager.updateCertificatesConfiguration(new CertificatesConfig(configurationTopics));
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
                AuthorizationRequest.builder().sessionId("ALLOW_ALL").operation("mqtt:publish")
                        .resource("mqtt:topic:foo").build();
        assertThat(authClient.canDevicePerform(authorizationRequest), is(true));
    }
}
