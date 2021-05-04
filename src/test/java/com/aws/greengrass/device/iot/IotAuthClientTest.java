/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

import com.aws.greengrass.device.exception.CloudServiceInteractionException;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.AccessDeniedException;
import software.amazon.awssdk.services.greengrassv2data.model.InternalServerException;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityRequest;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIdentityResponse;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIoTCertificateAssociationRequest;
import software.amazon.awssdk.services.greengrassv2data.model.VerifyClientDeviceIoTCertificateAssociationResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class IotAuthClientTest {
    private static final long TEST_TIME_OUT_SEC = 5L;

    @InjectMocks
    private IotAuthClient.Default iotAuthClient;

    @Mock
    private GreengrassServiceClientFactory clientFactory;

    @Mock
    private GreengrassV2DataClient client;

    @Captor
    private ArgumentCaptor<VerifyClientDeviceIdentityRequest> identityRequestCaptor;

    @Mock
    private Thing thing;

    @Mock
    private Certificate certificate;

    @Captor
    private ArgumentCaptor<VerifyClientDeviceIoTCertificateAssociationRequest> associationRequestCaptor;

    @BeforeEach
    void beforeEach() {
        lenient().when(clientFactory.getGreengrassV2DataClient()).thenReturn(client);
    }

    @Test
    void GIVEN_certificatePem_and_cloudProperResponse_WHEN_getActiveCertificateId_THEN_certificateIdReturned() {
        VerifyClientDeviceIdentityResponse response =
                VerifyClientDeviceIdentityResponse.builder().clientDeviceCertificateId("certificateId").build();
        when(client.verifyClientDeviceIdentity(any(VerifyClientDeviceIdentityRequest.class))).thenReturn(response);

        String certificateId = iotAuthClient.getActiveCertificateId("certificatePem");

        assertThat(certificateId, is("certificateId"));
        verify(client).verifyClientDeviceIdentity(identityRequestCaptor.capture());
        assertThat(identityRequestCaptor.getValue().clientDeviceCertificate(), is("certificatePem"));
    }

    @Test
    void GIVEN_cloudThrowException_WHEN_getActiveCertificateId_THEN_throwCloudInteractionException(
            ExtensionContext context) {
        ignoreExceptionOfType(context, ValidationException.class);
        when(client.verifyClientDeviceIdentity(any(VerifyClientDeviceIdentityRequest.class)))
                .thenThrow(ValidationException.class);

        assertThrows(CloudServiceInteractionException.class,
                () -> iotAuthClient.getActiveCertificateId("certificatePem"));
    }

    @Test
    void GIVEN_certificatePemEmpty_WHEN_getActiveCertificateId_THEN_throwIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> iotAuthClient.getActiveCertificateId(""));
    }

    @Test
    void GIVEN_threadGotInterrupted_WHEN_getActiveCertificateId_THEN_throwCloudInteractionException(
            ExtensionContext context) throws Exception {
        ignoreExceptionOfType(context, InterruptedException.class);
        ignoreExceptionOfType(context, InternalServerException.class);
        when(client.verifyClientDeviceIdentity(any(VerifyClientDeviceIdentityRequest.class)))
                .thenThrow(InternalServerException.class);

        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try {
                iotAuthClient.getActiveCertificateId("certificatePem");
            } catch (CloudServiceInteractionException e) {
                latch.countDown();
            }
        });
        thread.start();
        Thread.sleep(1000);
        thread.interrupt();
        assertThat(latch.await(TEST_TIME_OUT_SEC, TimeUnit.SECONDS), is(true));
    }

    @Test
    void GIVEN_certificateAndThing_and_cloudVerificationSuccess_WHEN_isThingAttachedToCertificate_THEN_returnTrue() {
        when(thing.getThingName()).thenReturn("thingName");
        when(certificate.getIotCertificateId()).thenReturn("certificateId");
        when(client.verifyClientDeviceIoTCertificateAssociation(
                any(VerifyClientDeviceIoTCertificateAssociationRequest.class)))
                .thenReturn(VerifyClientDeviceIoTCertificateAssociationResponse.builder().build());

        assertThat(iotAuthClient.isThingAttachedToCertificate(thing, certificate), is(true));
        verify(client).verifyClientDeviceIoTCertificateAssociation(associationRequestCaptor.capture());
        VerifyClientDeviceIoTCertificateAssociationRequest request = associationRequestCaptor.getValue();
        assertThat(request.clientDeviceCertificateId(), is("certificateId"));
        assertThat(request.clientDeviceThingName(), is("thingName"));
    }

    @Test
    void GIVEN_cloudThrowValidationException_WHEN_isThingAttachedToCertificate_THEN_returnFalse(
            ExtensionContext context) {
        ignoreExceptionOfType(context, ValidationException.class);
        when(thing.getThingName()).thenReturn("thingName");
        when(certificate.getIotCertificateId()).thenReturn("certificateId");
        when(client.verifyClientDeviceIoTCertificateAssociation(
                any(VerifyClientDeviceIoTCertificateAssociationRequest.class))).thenThrow(ValidationException.class);

        assertThat(iotAuthClient.isThingAttachedToCertificate(thing, certificate), is(false));
    }

    @Test
    void GIVEN_cloudThrowException_WHEN_isThingAttachedToCertificate_THEN_throwCloudInteractionException(
            ExtensionContext context) {
        ignoreExceptionOfType(context, AccessDeniedException.class);
        when(thing.getThingName()).thenReturn("thingName");
        when(certificate.getIotCertificateId()).thenReturn("certificateId");
        when(client.verifyClientDeviceIoTCertificateAssociation(
                any(VerifyClientDeviceIoTCertificateAssociationRequest.class))).thenThrow(AccessDeniedException.class);

        assertThrows(CloudServiceInteractionException.class,
                () -> iotAuthClient.isThingAttachedToCertificate(thing, certificate));
    }

    @Test
    void GIVEN_thingNameEmpty_WHEN_isThingAttachedToCertificate_THEN_throwIllegalArgumentException(
            ExtensionContext context) {
        ignoreExceptionOfType(context, IllegalArgumentException.class);
        when(thing.getThingName()).thenReturn("");

        assertThrows(IllegalArgumentException.class,
                () -> iotAuthClient.isThingAttachedToCertificate(thing, certificate));
    }

    @Test
    void GIVEN_iotCertificateIdEmpty_WHEN_isThingAttachedToCertificate_THEN_throwIllegalArgumentException(
            ExtensionContext context) {
        ignoreExceptionOfType(context, IllegalArgumentException.class);
        when(thing.getThingName()).thenReturn("thingName");
        when(certificate.getIotCertificateId()).thenReturn("");

        assertThrows(IllegalArgumentException.class,
                () -> iotAuthClient.isThingAttachedToCertificate(thing, certificate));
    }
}
