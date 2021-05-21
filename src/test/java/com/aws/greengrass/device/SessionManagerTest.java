/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device;


import com.aws.greengrass.certificatemanager.certificate.CertificateStore;
import com.aws.greengrass.device.exception.AuthorizationException;
import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.device.iot.Certificate;
import com.aws.greengrass.device.iot.IotAuthClient;
import com.aws.greengrass.device.iot.Thing;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.hamcrest.collection.IsMapContaining;
import org.hamcrest.collection.IsMapWithSize;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class SessionManagerTest {

    @InjectMocks
    private SessionManager sessionManager;

    @Mock
    private IotAuthClient iotAuthClient;

    @Mock
    private CertificateStore certificateStore;

    @Mock
    private ScheduledExecutorService scheduledExecutorService;

    @Test
    void GIVEN_createdSession_WHEN_findSession_THEN_sessionLastVisitUpdated() throws Exception {
        String id = sessionManager.createSession(new Certificate("pem", "certificateId"));
        SessionManager.SessionDecorator session = (SessionManager.SessionDecorator) sessionManager.findSession(id);
        Instant firstVisit = session.getLastVisit();
        Thread.sleep(100);
        session = (SessionManager.SessionDecorator) sessionManager.findSession(id);
        Instant secondVisit = session.getLastVisit();

        assertThat(secondVisit.isAfter(firstVisit), Is.is(true));
    }

    @Test
    void GIVEN_session_exist_WHEN_close_session_THEN_succeed() throws Exception {
        String id = sessionManager.createSession(new Certificate("pem", "certificateId"));
        assertThat(sessionManager.findSession(id), notNullValue());
        sessionManager.closeSession(id);
        assertThat(sessionManager.findSession(id), nullValue());
    }

    @Test
    void GIVEN_session_not_exist_WHEN_close_session_THEN_throw_exception() {
        assertThrows(AuthorizationException.class, () -> sessionManager.closeSession("id"));
    }

    @Test
    void GIVEN_sessionData_WHEN_refreshSession_THEN_invalidSessionClosed() throws Exception {
        Map<String, Session> sessionMap = prepareSessionHealthCheckData();
        sessionManager.setSessionMap(sessionMap);
        String pem1 = "pem1";
        String pem2 = "pem2";
        String pem1Hash = CertificateStore.computeCertificatePemHash(pem1);
        String pem2Hash = CertificateStore.computeCertificatePemHash(pem2);
        when(certificateStore.loadDeviceCertificate(pem1Hash)).thenReturn(pem1);
        when(certificateStore.loadDeviceCertificate(pem2Hash)).thenReturn(pem2);
        when(iotAuthClient.getActiveCertificateId(pem1)).thenReturn(Optional.empty());
        when(iotAuthClient.getActiveCertificateId(pem2)).thenReturn(Optional.of("id2"));
        Certificate certificate = new Certificate(pem2Hash, "id2");
        when(iotAuthClient.isThingAttachedToCertificate(new Thing("thing2"), certificate)).thenReturn(true);
        when(iotAuthClient.isThingAttachedToCertificate(new Thing("thing4"), certificate)).thenReturn(false);

        sessionManager.startSessionCheck();

        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);

        verify(scheduledExecutorService)
                .scheduleWithFixedDelay(runnableArgumentCaptor.capture(), eq(0L), eq(7L), eq(TimeUnit.DAYS));
        runnableArgumentCaptor.getValue().run();

        assertThat(sessionMap, IsMapWithSize.aMapWithSize(1));
        assertThat(sessionMap, IsMapContaining.hasKey("2"));
    }

    @Test
    void GIVEN_loadCertificateError_WHEN_refreshSession_THEN_closeSession(ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, IOException.class);
        Map<String, Session> sessionMap = new HashMap<>();
        addToSessionMap("1", "pem1", "id1", "thing1", sessionMap);
        sessionManager.setSessionMap(sessionMap);
        when(certificateStore.loadDeviceCertificate(CertificateStore.computeCertificatePemHash("pem1")))
                .thenThrow(IOException.class);

        sessionManager.refreshSessions();

        assertThat(sessionMap, IsMapWithSize.aMapWithSize(0));
    }

    @Test
    void GIVEN_certificateModified_WHEN_refreshSession_THEN_closeSession() throws Exception {
        Map<String, Session> sessionMap = new HashMap<>();
        addToSessionMap("1", "pem1", "id1", "thing1", sessionMap);
        sessionManager.setSessionMap(sessionMap);
        when(certificateStore.loadDeviceCertificate(CertificateStore.computeCertificatePemHash("pem1")))
                .thenReturn("pem11");

        sessionManager.refreshSessions();

        assertThat(sessionMap, IsMapWithSize.aMapWithSize(0));
    }

    @Test
    void GIVEN_getActiveCertificateIdException_WHEN_refreshSession_THEN_keepSessionOpen(ExtensionContext context)
            throws Exception {
        ignoreExceptionOfType(context, CloudServiceInteractionException.class);
        Map<String, Session> sessionMap = new HashMap<>();
        String pem1 = "pem1";
        String pem1Hash = CertificateStore.computeCertificatePemHash(pem1);
        addToSessionMap("1", pem1, "id1", "thing1", sessionMap);
        sessionManager.setSessionMap(sessionMap);
        when(certificateStore.loadDeviceCertificate(pem1Hash)).thenReturn(pem1);
        when(iotAuthClient.getActiveCertificateId(pem1)).thenThrow(CloudServiceInteractionException.class);
        Certificate certificate = new Certificate(pem1Hash, "id1");
        when(iotAuthClient.isThingAttachedToCertificate(new Thing("thing1"), certificate)).thenReturn(true);

        sessionManager.refreshSessions();

        assertThat(sessionMap, IsMapWithSize.aMapWithSize(1));
    }

    @Test
    void GIVEN_verifyThingAssociationException_WHEN_refreshSession_THEN_keepSessionOpen(ExtensionContext context)
            throws Exception {
        ignoreExceptionOfType(context, CloudServiceInteractionException.class);
        Map<String, Session> sessionMap = new HashMap<>();
        String pem1 = "pem1";
        String pem1Hash = CertificateStore.computeCertificatePemHash(pem1);
        addToSessionMap("1", pem1, "id1", "thing1", sessionMap);
        sessionManager.setSessionMap(sessionMap);
        when(certificateStore.loadDeviceCertificate(pem1Hash)).thenReturn(pem1);
        when(iotAuthClient.getActiveCertificateId(pem1)).thenReturn(Optional.of("id1"));
        Certificate certificate = new Certificate(pem1Hash, "id1");
        when(iotAuthClient.isThingAttachedToCertificate(new Thing("thing1"), certificate))
                .thenThrow(CloudServiceInteractionException.class);

        sessionManager.refreshSessions();

        assertThat(sessionMap, IsMapWithSize.aMapWithSize(1));
    }

    private Map<String, Session> prepareSessionHealthCheckData() {
        Map<String, Session> sessionMap = new HashMap<>();
        addToSessionMap("1", "pem1", "id1", "thing1", sessionMap);
        addToSessionMap("2", "pem2", "id2", "thing2", sessionMap);
        addToSessionMap("3", "pem1", "id1", "thing3", sessionMap);
        addToSessionMap("4", "pem2", "id2", "thing4", sessionMap);
        return sessionMap;
    }

    private void addToSessionMap(String id, String certificate, String certificateId, String thingName,
                                 Map<String, Session> sessionMap) {
        addToSessionMap(id, certificate, certificateId, thingName, null, sessionMap);
    }

    private void addToSessionMap(String id, String certificate, String certificateId, String thingName,
                                 Instant lastVisit, Map<String, Session> sessionMap) {
        String certificateHash = CertificateStore.computeCertificatePemHash(certificate);
        Session session = new SessionImpl(new Certificate(certificateHash, certificateId));
        session.putAttributeProvider(Thing.NAMESPACE, new Thing(thingName));
        sessionMap.put(id, new SessionManager.SessionDecorator(id, session, lastVisit));
    }

    @Test
    void GIVEN_scheduledSessionCheckFuture_WHEN_stopSessionCheck_THEN_cancelFuture() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        sessionManager.setSessionRefreshFuture(future);

        sessionManager.stopSessionCheck();

        verify(future).cancel(true);
    }

    @Test
    void GIVEN_sessionExpired_WHEN_checkSessionExpiry_THEN_closeSession() {
        long twoHoursInMillis = 2 * 60 * 60 * 1000L;
        Map<String, Session> sessionMap = new HashMap<>();
        addToSessionMap("1", "pem1", "id1", "thing1", Instant.now().minusMillis(twoHoursInMillis), sessionMap);
        addToSessionMap("2", "pem2", "id2", "thing2", Instant.now(), sessionMap);
        sessionManager.setSessionMap(sessionMap);

        sessionManager.startSessionExpiryCheck();

        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduledExecutorService).scheduleWithFixedDelay(runnableArgumentCaptor.capture(), eq(0L), eq(20L),
                eq(TimeUnit.MINUTES));
        runnableArgumentCaptor.getValue().run();

        assertThat(sessionMap, IsMapWithSize.aMapWithSize(1));
        assertThat(sessionMap, IsMapContaining.hasKey("2"));
    }

    @Test
    void GIVEN_mockedSchedulingService_WHEN_startSessionExpiryCheck_THEN_invokeProperMethod() {
        sessionManager.startSessionExpiryCheck();

        verify(scheduledExecutorService).scheduleWithFixedDelay(any(), eq(0L), eq(20L), eq(TimeUnit.MINUTES));
    }

    @Test
    void GIVEN_scheduledSessionCheckFuture_WHEN_stopSessionExpiryCheck_THEN_cancelFuture() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        sessionManager.setSessionExpiryCheckFuture(future);

        sessionManager.stopSessionExpiryCheck();

        verify(future).cancel(true);
    }
}
