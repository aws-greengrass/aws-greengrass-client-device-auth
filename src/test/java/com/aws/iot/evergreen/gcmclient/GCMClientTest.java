/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.gcmclient;

import com.aws.iot.evergreen.deployment.exceptions.AWSIotException;
import com.aws.iot.evergreen.iot.IotCloudHelper;
import com.aws.iot.evergreen.iot.IotConnectionManager;
import com.aws.iot.evergreen.testcommons.testutilities.EGExtension;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, EGExtension.class})
public class GCMClientTest extends EGExtension {
    private static final String ENDPOINT_TEST = UUID.randomUUID().toString();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private IotConnectionManager mockIotConnectionManager;

    @Mock
    private IotCloudHelper mockIotCloudHelper;

    GCMClient client;

    @BeforeEach
    public void setup() {
        client = new GCMClient(ENDPOINT_TEST, mockIotConnectionManager, mockIotCloudHelper);
    }

    @Test
    public void GIVEN_gcm_client_WHEN_get_certificate_called_and_cloud_returns_non_empty_result_THEN_string_returned()
            throws GCMClientException, AWSIotException, JsonProcessingException {
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> verbCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        final String csr = UUID.randomUUID().toString();
        GetCertificateRequest request = new GetCertificateRequest(csr);
        final GetCertificateResponse response = new GetCertificateResponse();
        response.setCertificate(UUID.randomUUID().toString());
        final String httpResponse = OBJECT_MAPPER.writeValueAsString(response);
        when(mockIotCloudHelper
                .sendHttpRequest(eq(mockIotConnectionManager), pathCaptor.capture(), verbCaptor.capture(),
                        bodyCaptor.capture())).thenReturn(httpResponse);
        GetCertificateResponse actualResponse = client.getCertificate(request);
        assertThat(actualResponse, is(response));
        assertThat(pathCaptor.getValue(), is(String.format("https://%s/greengrass/gcm/certificate", ENDPOINT_TEST)));
        assertThat(verbCaptor.getValue(), is("POST"));
        assertThat(bodyCaptor.getValue(), is(OBJECT_MAPPER.writeValueAsBytes(request)));
    }

    @Test
    public void GIVEN_gcm_client_WHEN_get_certificate_called_and_cloud_returns_empty_cert_result_THEN_throws()
            throws AWSIotException, JsonProcessingException {
        final String csr = UUID.randomUUID().toString();
        final GetCertificateResponse response = new GetCertificateResponse();
        response.setCertificate("");
        final String httpResponse = OBJECT_MAPPER.writeValueAsString(response);
        when(mockIotCloudHelper.sendHttpRequest(eq(mockIotConnectionManager), any(), any(), any()))
                .thenReturn(httpResponse);
        GCMClientException ex = Assertions
                .assertThrows(GCMClientException.class, () -> client.getCertificate(new GetCertificateRequest(csr)));
        assertThat(ex.getMessage(), containsString("Bad GCM response"));
    }

    @Test
    public void GIVEN_gcm_client_WHEN_get_certificate_called_and_cloud_returns_unparsable_result_THEN_throws()
            throws AWSIotException, JsonProcessingException {
        final String csr = UUID.randomUUID().toString();
        final GetCertificateResponse response = new GetCertificateResponse();
        response.setCertificate(UUID.randomUUID().toString());
        final String httpResponse = OBJECT_MAPPER.writeValueAsString(UUID.randomUUID().toString());
        when(mockIotCloudHelper.sendHttpRequest(eq(mockIotConnectionManager), any(), any(), any()))
                .thenReturn(httpResponse);
        GCMClientException ex = Assertions
                .assertThrows(GCMClientException.class, () -> client.getCertificate(new GetCertificateRequest(csr)));
        assertThat(ex.getMessage(), containsString("Unparsable GCM response"));
    }

    @Test
    public void GIVEN_gcm_client_WHEN_get_certificate_called_and_cloud_helper_throws_THEN_throws()
            throws AWSIotException, JsonProcessingException {
        final String csr = UUID.randomUUID().toString();
        final GetCertificateResponse response = new GetCertificateResponse();
        response.setCertificate(UUID.randomUUID().toString());
        when(mockIotCloudHelper.sendHttpRequest(eq(mockIotConnectionManager), any(), any(), any()))
                .thenThrow(new AWSIotException("TEST"));
        GCMClientException ex = Assertions
                .assertThrows(GCMClientException.class, () -> client.getCertificate(new GetCertificateRequest(csr)));
        assertThat(ex.getMessage(), containsString("Failed to get certificate from GCM"));
        assertThat(ex.getCause().getMessage(), containsString("TEST"));
    }
}
