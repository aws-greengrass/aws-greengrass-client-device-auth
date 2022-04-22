/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;


import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class SessionManagerTest {
    private static final String CREDENTIAL_TYPE = "mqtt";

    @Spy
    private SessionManager sessionManager;
    @Mock
    private MqttSessionFactory mockSessionFactory;
    @Mock
    private Session mockSession;
    @Mock
    private Session mockSession2;
    private final Map<String, String> credentialMap = ImmutableMap.of(
            "certificatePem", "PEM",
            "clientId", "clientId",
            "username", "",
            "password", ""
    );
    private final Map<String, String> credentialMap2 = ImmutableMap.of(
            "certificatePem", "PEM2",
            "clientId", "clientId2",
            "username", "",
            "password", ""
    );
    private final Map<String, String> invalidCredentialMap = ImmutableMap.of(
            "certificatePem", "BAD_PEM",
            "clientId", "clientId2",
            "username", "",
            "password", ""
    );

    @BeforeEach
    void beforeEach() throws AuthenticationException {
        SessionCreator.registerSessionFactory(CREDENTIAL_TYPE, mockSessionFactory);
        lenient().when(mockSessionFactory.createSession(credentialMap)).thenReturn(mockSession);
        lenient().when(mockSessionFactory.createSession(credentialMap2)).thenReturn(mockSession2);
        lenient().when(mockSessionFactory.createSession(invalidCredentialMap)).thenThrow(new AuthenticationException(""));
    }

    @AfterEach
    void afterEach() {
        SessionCreator.unregisterSessionFactory(CREDENTIAL_TYPE);
    }

    @Test
    void GIVEN_validDeviceCredentials_WHEN_createSession_THEN_sessionCreatedWithUniqueIds()
            throws AuthenticationException {
        String id1 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap);
        String id2 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap2);
        assertThat(id1, is(not(nullValue())));
        assertThat(id2, is(not(nullValue())));
        assertThat(id1, is(not(id2)));
        assertThat(sessionManager.findSession(id1), is(mockSession));
        assertThat(sessionManager.findSession(id2), is(mockSession2));
    }

    @Test
    void GIVEN_validDeviceCredentials_WHEN_createSessionTwice_THEN_oldSessionIsRemoved() throws AuthenticationException {
        String id1 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap);
        String id2 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap);
        assertThat(id1, is(not(nullValue())));
        assertThat(id2, is(not(nullValue())));
        assertThat(id1, is(not(id2)));
        assertThat(sessionManager.findSession(id1), is(nullValue()));
        assertThat(sessionManager.findSession(id2), is(mockSession));
    }

    @Test
    void GIVEN_invalidDeviceCredentials_WHEN_createSession_THEN_throwsAuthenticationException() {
        assertThrows(AuthenticationException.class, () -> sessionManager.createSession(CREDENTIAL_TYPE, invalidCredentialMap));
    }

    @Test
    void GIVEN_validExternalSessionID_WHEN_closeSession_THEN_sessionIsRemoved() throws AuthenticationException {
        String id1 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap);
        sessionManager.closeSession(id1);
        assertThat(sessionManager.findSession(id1), is(nullValue()));
    }

    @Test
    void GIVEN_invalidExternalSessionID_WHEN_closeSession_THEN_noActionNeeded() {
        // Should not throw
        sessionManager.closeSession("invalid ID");
    }

    @Test
    void GIVEN_externalIdCollision_WHEN_createSession_THEN_retryTillUniqueId() throws AuthenticationException {
        when(sessionManager.generateSessionId()).thenReturn("id1", "id1", "id1", "id2");

        String id1 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap);
        String id2 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap);
        assertThat(id1, is("id1"));
        assertThat(id2, is("id2"));
    }
}
