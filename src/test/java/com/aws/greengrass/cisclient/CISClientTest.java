/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cisclient;

import com.aws.greengrass.deployment.exceptions.AWSIotException;
import com.aws.greengrass.iot.IotCloudHelper;
import com.aws.greengrass.iot.IotConnectionManager;
import com.aws.greengrass.iot.model.IotCloudResponse;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CISClientTest extends GGExtension {
    private static final String ENDPOINT_TEST = UUID.randomUUID().toString();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private IotConnectionManager mockIotConnectionManager;

    @Mock
    private IotCloudHelper mockIotCloudHelper;

    CISClient client;

    @BeforeEach
    public void setup() {
        client = new CISClient(ENDPOINT_TEST, mockIotConnectionManager, mockIotCloudHelper);
    }

    @Test
    public void GIVEN_cis_client_WHEN_get_conn_info_called_and_cloud_returns_non_empty_result_THEN_valid_response()
            throws CISClientException, AWSIotException, JsonProcessingException {
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> verbCaptor = ArgumentCaptor.forClass(String.class);

        final String thingName = UUID.randomUUID().toString();
        GetConnectivityInfoRequest request = new GetConnectivityInfoRequest(thingName);
        final GetConnectivityInfoResponse response = new GetConnectivityInfoResponse();
        List<ConnectivityInfoItem> items = new ArrayList<>();
        items.add(new ConnectivityInfoItem(UUID.randomUUID().toString(), "localhost", null, 8000));
        response.setConnectivityInfoItems(items);
        final String httpResponse = OBJECT_MAPPER.writeValueAsString(response);
        final IotCloudResponse cloudResponse = new IotCloudResponse(httpResponse.getBytes(), 200);
        when(mockIotCloudHelper
                .sendHttpRequest(eq(mockIotConnectionManager), pathCaptor.capture(), verbCaptor.capture(), eq(null)))
                .thenReturn(cloudResponse);

        GetConnectivityInfoResponse actualResponse = client.getConnectivityInfo(request);
        assertThat(actualResponse, is(response));
        assertThat(pathCaptor.getValue(), is(String
                .format("https://%s/%s/%s", ENDPOINT_TEST, "greengrass" + "/connectivityInfo/thing", thingName)));
        assertThat(verbCaptor.getValue(), is("GET"));
    }

    @Test
    public void GIVEN_cis_client_WHEN_get_conn_info_called_and_cloud_returns_empty_list_THEN_throws()
            throws AWSIotException, JsonProcessingException {
        final String thingName = UUID.randomUUID().toString();
        final GetConnectivityInfoResponse response = new GetConnectivityInfoResponse();
        List<ConnectivityInfoItem> items = new ArrayList<>();
        response.setConnectivityInfoItems(items);
        final String httpResponse = OBJECT_MAPPER.writeValueAsString(response);
        final IotCloudResponse cloudResponse = new IotCloudResponse(httpResponse.getBytes(), 200);
        when(mockIotCloudHelper.sendHttpRequest(eq(mockIotConnectionManager), any(), any(), eq(null)))
                .thenReturn(cloudResponse);

        CISClientException ex = Assertions.assertThrows(CISClientException.class,
                () -> client.getConnectivityInfo(new GetConnectivityInfoRequest(thingName)));
        assertThat(ex.getMessage(), containsString("Bad CIS response"));
    }

    @Test
    public void GIVEN_cis_client_WHEN_get_conn_info_called_and_cloud_returns_unparsable_result_THEN_throws()
            throws AWSIotException {
        final String thingName = UUID.randomUUID().toString();
        final String httpResponse = "Some invalid response";
        final IotCloudResponse cloudResponse = new IotCloudResponse(httpResponse.getBytes(), 200);
        when(mockIotCloudHelper.sendHttpRequest(eq(mockIotConnectionManager), any(), any(), eq(null)))
                .thenReturn(cloudResponse);

        CISClientException ex = Assertions.assertThrows(CISClientException.class,
                () -> client.getConnectivityInfo(new GetConnectivityInfoRequest(thingName)));
        assertThat(ex.getMessage(), containsString("Unparsable CIS response"));
    }

    @Test
    public void GIVEN_cis_client_WHEN_get_conn_info_called_and_cloud_helper_throws_THEN_throws()
            throws AWSIotException {
        final String thingName = UUID.randomUUID().toString();
        when(mockIotCloudHelper.sendHttpRequest(eq(mockIotConnectionManager), any(), any(), eq(null)))
                .thenThrow(new AWSIotException("TEST"));
        CISClientException ex = Assertions.assertThrows(CISClientException.class,
                () -> client.getConnectivityInfo(new GetConnectivityInfoRequest(thingName)));
        assertThat(ex.getMessage(), containsString("Failed to get connectivity info from CIS"));
        assertThat(ex.getCause().getMessage(), containsString("TEST"));
    }
}
