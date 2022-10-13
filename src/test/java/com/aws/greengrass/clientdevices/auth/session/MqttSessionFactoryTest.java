/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.session;

import com.aws.greengrass.clientdevices.auth.DeviceAuthClient;
import com.aws.greengrass.clientdevices.auth.exception.AuthenticationException;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.CertificateFake;
import com.aws.greengrass.clientdevices.auth.iot.InvalidCertificateException;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.CertificateRegistry;
import com.aws.greengrass.clientdevices.auth.iot.Component;
import com.aws.greengrass.clientdevices.auth.iot.ThingRegistry;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.utils.ImmutableMap;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.aws.greengrass.clientdevices.auth.ClientDevicesAuthService.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class MqttSessionFactoryTest {
    @Mock
    private DeviceAuthClient mockDeviceAuthClient;
    @Mock
    private CertificateRegistry mockCertificateRegistry;
    @Mock
    private ThingRegistry mockThingRegistry;
    @Mock
    private SessionConfig mockSessionConfig;
    private MqttSessionFactory mqttSessionFactory;
    private final Map<String, String> credentialMap = ImmutableMap.of(
            "certificatePem", "PEM",
            "clientId", "clientId",
            "username", "",
            "password", ""
    );

    @BeforeEach
    void beforeEach() {
        lenient().when(mockSessionConfig.getClientDeviceTrustDurationHours()).thenReturn(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_HOURS);
        mqttSessionFactory = new MqttSessionFactory(mockDeviceAuthClient, mockCertificateRegistry, mockThingRegistry, mockSessionConfig);
    }

    @Test
    void GIVEN_credentialsWithUnknownClientId_WHEN_createSession_THEN_throwsAuthenticationException()
            throws InvalidCertificateException {
        when(mockCertificateRegistry.getCertificateFromPem(any()))
                .thenReturn(Optional.of(CertificateFake.activeCertificate()));
        when(mockThingRegistry.getOrCreateThing(any())).thenReturn(Thing.of("clientId"));
        when(mockThingRegistry.isThingAttachedToCertificate(any(), any())).thenReturn(false);

        Assertions.assertThrows(AuthenticationException.class,
                () -> mqttSessionFactory.createSession(credentialMap));
    }

    @Test
    void GIVEN_credentialsWithInvalidCertificate_WHEN_createSession_THEN_throwsAuthenticationException()
            throws InvalidCertificateException {
        when(mockCertificateRegistry.getCertificateFromPem(any())).thenReturn(Optional.empty());
        Assertions.assertThrows(AuthenticationException.class,
                () -> mqttSessionFactory.createSession(credentialMap));
    }

    @Test
    void GIVEN_credentialsWithCertificate_WHEN_createSession_AND_cloudError_THEN_throwsAuthenticationException()
            throws InvalidCertificateException {
        when(mockCertificateRegistry.getCertificateFromPem(any()))
                .thenReturn(Optional.of(CertificateFake.activeCertificate()));
        when(mockThingRegistry.getOrCreateThing(any())).thenReturn(Thing.of("clientId"));
        when(mockThingRegistry.isThingAttachedToCertificate(any(), any()))
                .thenThrow(CloudServiceInteractionException.class);
        Assertions.assertThrows(AuthenticationException.class,
                () -> mqttSessionFactory.createSession(credentialMap));
    }

    @Test
    void GIVEN_credentialsWithValidClientId_WHEN_createSession_THEN_returnsSession()
            throws AuthenticationException, InvalidCertificateException {
        Certificate activeCert = CertificateFake.activeCertificate("fake-cert");
        when(mockCertificateRegistry.getCertificateFromPem(any()))
                .thenReturn(Optional.of(activeCert));
        Map<String, Instant> attachedCerts = new HashMap<>();
        // attached certificate within trust duration
        attachedCerts.put(activeCert.getCertificateId(), Instant.now());
        when(mockThingRegistry.getOrCreateThing("clientId")).thenReturn(Thing.of("clientId", attachedCerts));
        when(mockThingRegistry.isThingAttachedToCertificate(any(), any())).thenReturn(true);

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

    @Test
    void GIVEN_credentialsWithCertificateBeyondTrustDuration_WHEN_createSession_THEN_throwsAuthenticationException()
            throws InvalidCertificateException {
        Certificate activeCert = CertificateFake.activeCertificate("fake-cert", Instant.EPOCH);
        when(mockCertificateRegistry.getCertificateFromPem(any()))
                .thenReturn(Optional.of(activeCert));

        Assertions.assertThrows(AuthenticationException.class,
                () -> mqttSessionFactory.createSession(credentialMap));
    }

    @Test
    void GIVEN_thingWithAttachedCertBeyondTrustDuration_WHEN_createSession_THEN_throwsAuthenticationException()
            throws InvalidCertificateException {
        Certificate activeCert = CertificateFake.activeCertificate();
        when(mockCertificateRegistry.getCertificateFromPem(any()))
                .thenReturn(Optional.of(activeCert));
        Map<String, Instant> attachedCerts = new HashMap<>();
        // lastVerified date set to the epoch time
        attachedCerts.put(activeCert.getCertificateId(), Instant.EPOCH);
        when(mockThingRegistry.getOrCreateThing("clientId")).thenReturn(Thing.of("clientId", attachedCerts));
        when(mockThingRegistry.isThingAttachedToCertificate(any(), any())).thenReturn(true);

        Assertions.assertThrows(AuthenticationException.class,
                () -> mqttSessionFactory.createSession(credentialMap));
    }
}
