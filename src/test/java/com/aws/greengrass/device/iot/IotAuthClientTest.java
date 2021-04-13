/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.iot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.iot.model.CertificateDescription;
import software.amazon.awssdk.services.iot.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.iot.model.DescribeCertificateResponse;
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsRequest;
import software.amazon.awssdk.services.iot.model.ListThingPrincipalsResponse;

import java.util.Arrays;
import java.util.List;

@ExtendWith({MockitoExtension.class})
public class IotAuthClientTest {
    @Mock
    private IotClient mockIotClient;

    @Test
    public void GIVEN_certificateId_WHEN_downloadSingleDeviceCertificate_THEN_certificatePemIsReturned() {
        IotAuthClient.Default betaClient = new IotAuthClient.Default(mockIotClient);

        Mockito.when(mockIotClient.describeCertificate(Mockito.any(DescribeCertificateRequest.class)))
                .thenAnswer(i -> {
                    DescribeCertificateRequest request = i.getArgument(0);
                    DescribeCertificateResponse response = Mockito.mock(DescribeCertificateResponse.class);
                    CertificateDescription mockDescription = Mockito.mock(CertificateDescription.class);
                    Mockito.when(response.certificateDescription()).thenReturn(mockDescription);
                    Mockito.when(mockDescription.certificatePem()).thenReturn(request.certificateId() + "-CERT");
                    return response;
                });

        String certPem = betaClient.downloadSingleDeviceCertificate("testCertId");
        Assertions.assertEquals("testCertId-CERT", certPem);
    }

    @Test
    public void GIVEN_ThingWithSingleCertPrincipal_WHEN_listThingCertificatePrincipals_THEN_certIdIsReturned() {
        IotAuthClient.Default betaClient = new IotAuthClient.Default(mockIotClient);
        ListThingPrincipalsResponse mockListThingPrincipalsResponse = Mockito.mock(ListThingPrincipalsResponse.class);

        Mockito.when(mockIotClient.listThingPrincipals((ListThingPrincipalsRequest) Mockito.any()))
                .thenReturn(mockListThingPrincipalsResponse);
        Mockito.when(mockListThingPrincipalsResponse.principals())
                .thenReturn(Arrays.asList("arn:aws:iot:us-west-2:123456789012:cert/33475ac865079a5ffd5ecd44240640349293facc760642d7d8d5dbb6b4c86893"));

        List<String> certPrincipals = betaClient.listThingCertificatePrincipals("testThing");
        Assertions.assertEquals("33475ac865079a5ffd5ecd44240640349293facc760642d7d8d5dbb6b4c86893", certPrincipals.get(0));
    }

    @Test
    public void GIVEN_ThingWithMultiplePrincipals_WHEN_listThingCertificatePrincipals_THEN_certIdsAreReturned() {
        IotAuthClient.Default betaClient = new IotAuthClient.Default(mockIotClient);
        ListThingPrincipalsResponse mockListThingPrincipalsResponse = Mockito.mock(ListThingPrincipalsResponse.class);

        Mockito.when(mockIotClient.listThingPrincipals((ListThingPrincipalsRequest) Mockito.any()))
                .thenReturn(mockListThingPrincipalsResponse);
        Mockito.when(mockListThingPrincipalsResponse.principals())
                .thenReturn(Arrays.asList("arn:aws:iot:us-west-2:123456789012:cert/33475ac865079a5ffd5ecd44240640349293facc760642d7d8d5dbb6b4c86893",
                        "arn:aws:cognito-identity:us-west-2:123456789012:identitypool/cognito_id",
                        "arn:aws:iot:us-west-2:123456789012:cert/33475ac865079a5ffd5ecd44240640349293facc760642d7d8d5dbb6b4c86894"));

        List<String> certPrincipals = betaClient.listThingCertificatePrincipals("testThing");
        Assertions.assertEquals("33475ac865079a5ffd5ecd44240640349293facc760642d7d8d5dbb6b4c86893", certPrincipals.get(0));
        Assertions.assertEquals("33475ac865079a5ffd5ecd44240640349293facc760642d7d8d5dbb6b4c86894", certPrincipals.get(1));
    }

    @Test
    public void GIVEN_ThingWithNoCertPrincipals_WHEN_listThingCertificatePrincipals_THEN_emptyListIsReturned() {
        IotAuthClient.Default betaClient = new IotAuthClient.Default(mockIotClient);
        ListThingPrincipalsResponse mockListThingPrincipalsResponse = Mockito.mock(ListThingPrincipalsResponse.class);

        Mockito.when(mockIotClient.listThingPrincipals((ListThingPrincipalsRequest) Mockito.any()))
                .thenReturn(mockListThingPrincipalsResponse);
        Mockito.when(mockListThingPrincipalsResponse.principals())
                .thenReturn(Arrays.asList("arn:aws:cognito-identity:us-west-2:123456789012:identitypool/cognito_id"));

        List<String> certPrincipals = betaClient.listThingCertificatePrincipals("testThing");
        Assertions.assertTrue(certPrincipals.isEmpty());
    }
}
