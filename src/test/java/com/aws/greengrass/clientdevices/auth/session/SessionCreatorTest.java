/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class SessionCreatorTest {
    private static final String mqttCredentialType = "mqtt";
    private static final String unknownCredentialType = "unknown";

    private SessionCreator sessionCreator;

    @Mock
    private MqttSessionFactory mqttSessionFactory;

    @BeforeEach
    void beforeEach() {
        sessionCreator = new SessionCreator();
    }

    @Test
    void GIVEN_noRegisteredFactories_WHEN_createSession_THEN_throwsException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> sessionCreator.createSession(mqttCredentialType, new HashMap<>()));
    }

    @Test
    void GIVEN_registeredMqttSessionFactory_WHEN_createSessionWithMqttCredentials_THEN_sessionCreationSucceeds()
            throws AuthenticationException {
        Session mockSession = mock(SessionImpl.class);
        when(mqttSessionFactory.createSession(any())).thenReturn(mockSession);

        sessionCreator.registerSessionFactory(mqttCredentialType, mqttSessionFactory);
        assertThat(sessionCreator.createSession(mqttCredentialType, new HashMap<>()), is(mockSession));
    }

    @Test
    void GIVEN_registeredMqttSessionFactory_WHEN_createSession_WithNonMqttCredentials_THEN_throwsException() {
        sessionCreator.registerSessionFactory(mqttCredentialType, mqttSessionFactory);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> sessionCreator.createSession(unknownCredentialType, new HashMap<>()));
    }
}
