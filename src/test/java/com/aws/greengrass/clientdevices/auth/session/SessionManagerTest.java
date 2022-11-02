/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;


import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class SessionManagerTest {
    private static final String CREDENTIAL_TYPE = "mqtt";
    private static final int MOCK_SESSION_CAPACITY = 10;

    private SessionCreator sessionCreator;
    private SessionManager sessionManager;
    @Mock
    private MqttSessionFactory mockSessionFactory;
    @Mock
    private SessionConfig mockSessionConfig;
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
        lenient().when(mockSessionConfig.getSessionCapacity()).thenReturn(MOCK_SESSION_CAPACITY);
        sessionCreator = new SessionCreator();
        sessionManager = new SessionManager(sessionCreator, mockSessionConfig);
        sessionCreator.registerSessionFactory(CREDENTIAL_TYPE, mockSessionFactory);
        lenient().when(mockSessionFactory.createSession(credentialMap)).thenReturn(mockSession);
        lenient().when(mockSessionFactory.createSession(credentialMap2)).thenReturn(mockSession2);
        lenient().when(mockSessionFactory.createSession(invalidCredentialMap)).thenThrow(new AuthenticationException(""));
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

    // TODO: Re-enable this test
    /*
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
     */

    @Test
    void GIVEN_invalidDeviceCredentials_WHEN_createSession_THEN_throwsAuthenticationException() {
        assertThrows(AuthenticationException.class, () -> sessionManager.createSession(CREDENTIAL_TYPE, invalidCredentialMap));
    }

    @Test
    void GIVEN_maxOpenSessions_WHEN_createSession_THEN_oldestSessionIsEvicted()
            throws AuthenticationException {
        reset(mockSessionConfig);
        reset(mockSessionFactory);

        Map<String, String> credentialMap1 = ImmutableMap.of(
                "certificatePem", "PEM1",
                "clientId", "clientId1",
                "username", "user1",
                "password", ""
        );
        Map<String, String> credentialMap2 = ImmutableMap.of(
                "certificatePem", "PEM2",
                "clientId", "clientId2",
                "username", "user2",
                "password", ""
        );
        Map<String, String> credentialMap3 = ImmutableMap.of(
                "certificatePem", "PEM3",
                "clientId", "clientId3",
                "username", "user3",
                "password", ""
        );
        Map<String, String> credentialMap4 = ImmutableMap.of(
                "certificatePem", "PEM4",
                "clientId", "clientId4",
                "username", "user4",
                "password", ""
        );

        Session mockSession1 = mock(Session.class);
        Session mockSession2 = mock(Session.class);
        Session mockSession3 = mock(Session.class);
        Session mockSession4 = mock(Session.class);
        when(mockSessionFactory.createSession(credentialMap1)).thenReturn(mockSession1);
        when(mockSessionFactory.createSession(credentialMap2)).thenReturn(mockSession2);
        when(mockSessionFactory.createSession(credentialMap3)).thenReturn(mockSession3);
        when(mockSessionFactory.createSession(credentialMap4)).thenReturn(mockSession4);

        int sessionCapacity = 3;
        when(mockSessionConfig.getSessionCapacity()).thenReturn(sessionCapacity);

        // fill session cache to its capacity
        String id1 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap1);
        String id2 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap2);
        String id3 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap3);
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotNull(id3);
        assertThat(id1, is(not(id2)));

        // retrieve session id1, which moves this session to the end of eviction queue;
        // making session id2 the eldest session (in access order)
        sessionManager.findSession(id1);

        // create a new session when Session cache has already reached its capacity
        String id4 = sessionManager.createSession(CREDENTIAL_TYPE, credentialMap4);

        assertThat(sessionManager.findSession(id1), is(mockSession1));
        // session id2 should have been evicted
        assertNull(sessionManager.findSession(id2));
        assertThat(sessionManager.findSession(id3), is(mockSession3));
        assertThat(sessionManager.findSession(id4), is(mockSession4));
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
}
