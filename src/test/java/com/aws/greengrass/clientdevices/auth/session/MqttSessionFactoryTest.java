/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.DeviceAuthClient;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.Component;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class MqttSessionFactoryTest {
    @Mock
    private IotAuthClient mockIotAuthClient;
    @Mock
    private DeviceAuthClient mockDeviceAuthClient;
    @Mock
    private CertificateRegistry mockCertificateRegistry;
    private MqttSessionFactory mqttSessionFactory;
    private final Map<String, String> credentialMap = ImmutableMap.of(
            "certificatePem", "PEM",
            "clientId", "clientId",
            "username", "",
            "password", ""
    );

    @BeforeEach
    void beforeEach() {
        mqttSessionFactory = new MqttSessionFactory(mockIotAuthClient, mockDeviceAuthClient, mockCertificateRegistry);
    }

    @Test
    void GIVEN_credentialsWithUnknownClientId_WHEN_createSession_THEN_throwsAuthenticationException() {
        when(mockCertificateRegistry.getIotCertificateIdForPem(any())).thenReturn(Optional.of("id"));
        when(mockIotAuthClient.isThingAttachedToCertificate(any(), any())).thenReturn(false);

        Assertions.assertThrows(AuthenticationException.class,
                () -> mqttSessionFactory.createSession(credentialMap));
    }

    @Test
    void GIVEN_credentialsWithInvalidCertificate_WHEN_createSession_THEN_throwsAuthenticationException() {
        when(mockCertificateRegistry.getIotCertificateIdForPem(any())).thenReturn(Optional.empty());
        Assertions.assertThrows(AuthenticationException.class,
                () -> mqttSessionFactory.createSession(credentialMap));
    }

    @Test
    void GIVEN_credentialsWithCertificate_WHEN_createSession_AND_cloudError_THEN_throwsAuthenticationException() {
        when(mockCertificateRegistry.getIotCertificateIdForPem(any())).thenReturn(Optional.of("id"));
        when(mockIotAuthClient.isThingAttachedToCertificate(any(), any()))
                .thenThrow(CloudServiceInteractionException.class);
        Assertions.assertThrows(AuthenticationException.class,
                () -> mqttSessionFactory.createSession(credentialMap));
    }

    @Test
    void GIVEN_credentialsWithValidClientId_WHEN_createSession_THEN_returnsSession() throws AuthenticationException {
        when(mockCertificateRegistry.getIotCertificateIdForPem(any())).thenReturn(Optional.of("id"));
        when(mockIotAuthClient.isThingAttachedToCertificate(any(), any())).thenReturn(true);

        Session session = mqttSessionFactory.createSession(credentialMap);
        assertThat(session, is(IsNull.notNullValue()));
    }

    @Test
    void GIVEN_componentWithValidClientId_WHEN_createSession_THEN_returnsSession() throws AuthenticationException {
        when(mockDeviceAuthClient.isGreengrassComponent(anyString())).thenReturn(true);

        Session session = mqttSessionFactory.createSession(credentialMap);
        assertThat(session, is(IsNull.notNullValue()));
        assertThat(session.getSessionAttribute(Component.NAMESPACE, "component"), notNullValue());
    }
}
