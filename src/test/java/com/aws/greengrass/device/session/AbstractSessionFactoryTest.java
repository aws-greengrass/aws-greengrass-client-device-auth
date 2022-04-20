/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;

import com.aws.greengrass.device.exception.AuthenticationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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

@ExtendWith({MockitoExtension.class})
public class AbstractSessionFactoryTest {
    private static final String mqttCredentialType = "mqtt";
    private static final String unknownCredentialType = "unknown";

    @Mock
    private MqttSessionFactory mqttSessionFactory;

    @AfterEach
    void afterEach() {
        AbstractSessionFactory.unregisterSessionFactory(mqttCredentialType);
    }

    @Test
    void GIVEN_noRegisteredFactories_WHEN_createSession_THEN_throwsException() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AbstractSessionFactory.createSession(mqttCredentialType, new HashMap<>()));
    }

    @Test
    void GIVEN_registeredMqttSessionFactory_WHEN_createSessionWithMqttCredentials_THEN_sessionCreationSucceeds()
            throws AuthenticationException {
        Session mockSession = mock(SessionImpl.class);
        when(mqttSessionFactory.createSession(any())).thenReturn(mockSession);

        AbstractSessionFactory.registerSessionFactory(mqttCredentialType, mqttSessionFactory);
        assertThat(AbstractSessionFactory.createSession(mqttCredentialType, new HashMap<>()), is(mockSession));
    }

    @Test
    void GIVEN_registeredMqttSessionFactory_WHEN_createSession_WithNonMqttCredentials_THEN_throwsException() {
        AbstractSessionFactory.registerSessionFactory(mqttCredentialType, mqttSessionFactory);
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> AbstractSessionFactory.createSession(unknownCredentialType, new HashMap<>()));
    }
}
