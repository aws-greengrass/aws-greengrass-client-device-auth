/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

import com.aws.greengrass.clientdevices.auth.certificate.CertificateHelper;
import com.aws.greengrass.clientdevices.auth.certificate.CertificateStore;
import com.aws.greengrass.clientdevices.auth.exception.CloudServiceInteractionException;
import com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;
import com.aws.greengrass.clientdevices.auth.iot.Certificate;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClient;
import com.aws.greengrass.clientdevices.auth.iot.IotAuthClientFake;
import com.aws.greengrass.clientdevices.auth.iot.Thing;
import com.aws.greengrass.clientdevices.auth.iot.dto.VerifyThingAttachedToCertificateDTO;
import com.aws.greengrass.clientdevices.auth.iot.infra.ThingRegistry;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;

import static com.aws.greengrass.clientdevices.auth.configuration.SecurityConfiguration.DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES;
import static com.aws.greengrass.clientdevices.auth.helpers.CertificateTestHelpers.createClientCertificate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
class VerifyThingAttachedToCertificateTest {
    @Mock
    private NetworkStateProvider.Default mockNetworkState;
    @Mock
    private ThingRegistry mockThingRegistry;
    private IotAuthClientFake iotAuthClientFake;
    private VerifyThingAttachedToCertificate verifyThingAttachedToCertificate;

    @BeforeEach
    void beforeEach() {
        iotAuthClientFake = new IotAuthClientFake();
        verifyThingAttachedToCertificate =
                new VerifyThingAttachedToCertificate(iotAuthClientFake, mockThingRegistry, mockNetworkState);
    }

    @AfterEach
    void tearDown() {
        Thing.updateMetadataTrustDurationMinutes(DEFAULT_CLIENT_DEVICE_TRUST_DURATION_MINUTES);
    }

    @Test
    void GIVEN_validDtoAndNetworkUp_WHEN_verifyThingAttachedToCertificate_THEN_returnCloudResult() throws Exception {
        Thing thing = Thing.of("thing-1");
        X509Certificate certificate = createTestClientCertificate();
        String certPem = CertificateHelper.toPem(certificate);
        Certificate thingCertificate = Certificate.fromPem(certPem);
        iotAuthClientFake.attachCertificateToThing(thing.getThingName(), certPem);
        VerifyThingAttachedToCertificateDTO dto =
                new VerifyThingAttachedToCertificateDTO(thing.getThingName(), thingCertificate.getCertificateId());

        when(mockNetworkState.getConnectionState()).thenReturn(NetworkStateProvider.ConnectionState.NETWORK_UP);
        when(mockThingRegistry.getThing(thing.getThingName())).thenReturn(thing);

        VerifyThingAttachedToCertificate.Result result = verifyThingAttachedToCertificate.apply(dto);
        Instant lastAttached = thing.certificateLastAttachedOn(thingCertificate.getCertificateId()).orElseThrow(RuntimeException::new);

        // positive result
        assertThat(result, is(VerifyThingAttachedToCertificate.Result.builder()
                .thingAttachedToCertificate(true)
                .verificationSource(VerifyThingAttachedToCertificate.Result.VerificationSource.CLOUD)
                .lastAttached(lastAttached)
                .attachmentExpiration(lastAttached.plus(Duration.ofMinutes(1)))
                .build()));

        // negative result
        iotAuthClientFake.detachCertificateFromThing(thing.getThingName(), certPem);
        result = verifyThingAttachedToCertificate.apply(dto);
        assertThat(result, is(VerifyThingAttachedToCertificate.Result.builder()
                .thingAttachedToCertificate(false)
                .verificationSource(VerifyThingAttachedToCertificate.Result.VerificationSource.CLOUD)
                .build()));

    }

    @Test
    void GIVEN_validDtoAndNetworkDown_WHEN_verifyThingAttachedToCertificate_THEN_returnLocalResult() throws Exception {
        Thing thing = Thing.of("thing-1");
        X509Certificate certificate = createTestClientCertificate();
        String certPem = CertificateHelper.toPem(certificate);
        Certificate thingCertificate = Certificate.fromPem(certPem);
        thing.attachCertificate(thingCertificate.getCertificateId());
        VerifyThingAttachedToCertificateDTO dto =
                new VerifyThingAttachedToCertificateDTO(thing.getThingName(), thingCertificate.getCertificateId());

        when(mockNetworkState.getConnectionState()).thenReturn(NetworkStateProvider.ConnectionState.NETWORK_DOWN);
        when(mockThingRegistry.getThing(thing.getThingName())).thenReturn(thing);

        VerifyThingAttachedToCertificate.Result result = verifyThingAttachedToCertificate.apply(dto);
        Instant lastAttached = thing.certificateLastAttachedOn(thingCertificate.getCertificateId()).orElseThrow(RuntimeException::new);

        // positive result
        assertThat(result, is(VerifyThingAttachedToCertificate.Result.builder()
                .thingAttachedToCertificate(true)
                .verificationSource(VerifyThingAttachedToCertificate.Result.VerificationSource.LOCAL)
                .lastAttached(lastAttached)
                .attachmentExpiration(lastAttached.plus(Duration.ofMinutes(1)))
                .build()));

        // negative result
        thing.detachCertificate(thingCertificate.getCertificateId());
        result = verifyThingAttachedToCertificate.apply(dto);
        assertThat(result, is(VerifyThingAttachedToCertificate.Result.builder()
                .thingAttachedToCertificate(false)
                .verificationSource(VerifyThingAttachedToCertificate.Result.VerificationSource.LOCAL)
                .build()));
    }

    @Test
    void GIVEN_validDtoAndNetworkDownAndAttachmentExpired_WHEN_verifyThingAttachedToCertificate_THEN_localValidationFails() throws Exception {
        Thing thing = Thing.of("thing-1");
        X509Certificate certificate = createTestClientCertificate();
        String certPem = CertificateHelper.toPem(certificate);
        Certificate thingCertificate = Certificate.fromPem(certPem);
        thing.attachCertificate(thingCertificate.getCertificateId());
        VerifyThingAttachedToCertificateDTO dto =
                new VerifyThingAttachedToCertificateDTO(thing.getThingName(), thingCertificate.getCertificateId());

        when(mockNetworkState.getConnectionState()).thenReturn(NetworkStateProvider.ConnectionState.NETWORK_DOWN);
        when(mockThingRegistry.getThing(thing.getThingName())).thenReturn(thing);

        // expire attachments
        Thing.updateMetadataTrustDurationMinutes(0);

        VerifyThingAttachedToCertificate.Result result = verifyThingAttachedToCertificate.apply(dto);
        Instant lastAttached = thing.certificateLastAttachedOn(thingCertificate.getCertificateId()).orElseThrow(RuntimeException::new);
        assertThat(result, is(VerifyThingAttachedToCertificate.Result.builder()
                .thingAttachedToCertificate(false)
                .lastAttached(lastAttached)
                .attachmentExpiration(lastAttached)
                .verificationSource(VerifyThingAttachedToCertificate.Result.VerificationSource.LOCAL)
                .build()));

    }

    @Test
    void GIVEN_networkUpButFailedCloudCall_WHEN_verifyThingAttachedToCertificate_THEN_returnLocalResult()
            throws Exception {
        Thing thing = Thing.of("thing-1");
        X509Certificate certificate = createTestClientCertificate();
        String certPem = CertificateHelper.toPem(certificate);
        Certificate thingCertificate = Certificate.fromPem(certPem);
        thing.attachCertificate(thingCertificate.getCertificateId());
        VerifyThingAttachedToCertificateDTO dto =
                new VerifyThingAttachedToCertificateDTO(thing.getThingName(), thingCertificate.getCertificateId());

        // set up VerifyThingAttachedToCertificate usecase with mock iot auth client
        // to be able to throw exceptions during cloud calls
        IotAuthClient mockIotAuthClient = Mockito.mock(IotAuthClient.class);
        doThrow(CloudServiceInteractionException.class).when(mockIotAuthClient)
                .isThingAttachedToCertificate(any(), anyString());
        VerifyThingAttachedToCertificate verifyThingAttachedToCertificate =
                new VerifyThingAttachedToCertificate(mockIotAuthClient, mockThingRegistry, mockNetworkState);

        when(mockNetworkState.getConnectionState()).thenReturn(NetworkStateProvider.ConnectionState.NETWORK_UP);
        when(mockThingRegistry.getThing(thing.getThingName())).thenReturn(thing);


        VerifyThingAttachedToCertificate.Result result = verifyThingAttachedToCertificate.apply(dto);
        Instant lastAttached = thing.certificateLastAttachedOn(thingCertificate.getCertificateId()).orElseThrow(RuntimeException::new);

        // positive result
        assertThat(result, is(VerifyThingAttachedToCertificate.Result.builder()
                .thingAttachedToCertificate(true)
                .verificationSource(VerifyThingAttachedToCertificate.Result.VerificationSource.LOCAL)
                .lastAttached(lastAttached)
                .attachmentExpiration(lastAttached.plus(Duration.ofMinutes(1)))
                .build()));

        // negative result
        thing.detachCertificate(thingCertificate.getCertificateId());
        result = verifyThingAttachedToCertificate.apply(dto);
        assertThat(result, is(VerifyThingAttachedToCertificate.Result.builder()
                .thingAttachedToCertificate(false)
                .verificationSource(VerifyThingAttachedToCertificate.Result.VerificationSource.LOCAL)
                .build()));
    }

    private X509Certificate createTestClientCertificate() throws Exception {
        KeyPair rootKeyPair = CertificateStore.newRSAKeyPair(2048);
        X509Certificate rootCA = CertificateTestHelpers.createRootCertificateAuthority("root", rootKeyPair);
        KeyPair clientKeyPair = CertificateStore.newRSAKeyPair(2048);
        return createClientCertificate(rootCA, "AWS IoT Certificate", clientKeyPair.getPublic(),
                rootKeyPair.getPrivate());
    }
}
