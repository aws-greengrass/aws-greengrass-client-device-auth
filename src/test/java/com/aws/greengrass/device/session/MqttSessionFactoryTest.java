/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.session;

import com.aws.greengrass.device.exception.AuthenticationException;
import com.aws.greengrass.device.iot.IotAuthClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.Map;

import org.hamcrest.core.IsNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class})
public class MqttSessionFactoryTest {
    @Mock
    private IotAuthClient mockIotAuthClient;
    private MqttSessionFactory mqttSessionFactory;
    private final Map<String, String> credentialMap = ImmutableMap.of(
            "certificatePem", "PEM",
            "clientId", "clientId",
            "username", "",
            "password", ""
    );

    @BeforeEach
    void beforeEach() {
        mqttSessionFactory = new MqttSessionFactory(mockIotAuthClient);
    }

    @Test
    void GIVEN_credentialsWithUnknownClientId_WHEN_createSession_THEN_throwsAuthenticationException() {
        when(mockIotAuthClient.isThingAttachedToCertificate(any(), any())).thenReturn(false);

        Assertions.assertThrows(AuthenticationException.class,
                () -> mqttSessionFactory.createSession(credentialMap));
    }

    @Test
    void GIVEN_credentialsWithValidClientId_WHEN_createSession_THEN_returnsSession() throws AuthenticationException {
        when(mockIotAuthClient.isThingAttachedToCertificate(any(), any())).thenReturn(true);

        Session session = mqttSessionFactory.createSession(credentialMap);
        assertThat(session, is(IsNull.notNullValue()));
    }
}
