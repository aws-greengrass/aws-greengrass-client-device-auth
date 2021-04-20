package com.aws.greengrass.dcmclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.handler.SyncClientHandler;
import software.amazon.awssdk.services.greengrass.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrass.model.GetConnectivityInfoRequest;
import software.amazon.awssdk.services.greengrass.model.GetConnectivityInfoResponse;
import software.amazon.awssdk.services.greengrass.model.UpdateConnectivityInfoRequest;
import software.amazon.awssdk.services.greengrass.model.UpdateConnectivityInfoResponse;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({MockitoExtension.class})
public class DataPlaneClientDefaultTest {
    private DataPlaneDefaultClient dataPlaneClient;
    @Mock
    private SyncClientHandler clientHandler;

    @Test
    public void GIVEN_update_connectivity_request_WHEN_valid_THEN_update_connectivity_info_response_returned() {

        UpdateConnectivityInfoRequest updateConnectivityInfoRequest = UpdateConnectivityInfoRequest.builder().build();
        Mockito.doReturn(UpdateConnectivityInfoResponse.builder().version("1.0")
                .build()).when(clientHandler).execute(any());

        dataPlaneClient = new DataPlaneDefaultClient(SdkClientConfiguration.builder().build(), clientHandler);
        UpdateConnectivityInfoResponse updateConnectivityInfoResponse =
                dataPlaneClient.updateConnectivityInfo(updateConnectivityInfoRequest);

        assertEquals(updateConnectivityInfoResponse.version(), "1.0");
    }

    @Test
    public void GIVEN_update_connectivity_request_WHEN_null_THEN_null_returned() {
        dataPlaneClient = new DataPlaneDefaultClient(SdkClientConfiguration.builder().build(), clientHandler);
        UpdateConnectivityInfoResponse updateConnectivityInfoResponse =
                dataPlaneClient.updateConnectivityInfo(null);

        assertNull(updateConnectivityInfoResponse);
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void GIVEN_get_connectivity_request_WHEN_valid_THEN_get_connectivity_info_response_returned() {
        GetConnectivityInfoRequest getConnectivityInfoRequest = GetConnectivityInfoRequest.builder().build();
        dataPlaneClient = new DataPlaneDefaultClient(SdkClientConfiguration.builder().build(), clientHandler);

        ConnectivityInfo connectivityInfo = ConnectivityInfo.builder().hostAddress("172.8.8.10")
                .metadata("").id("172.8.8.10").portNumber(8883).build();
        ConnectivityInfo connectivityInfo1 = ConnectivityInfo.builder().hostAddress("localhost")
                .metadata("").id("localhost").portNumber(8883).build();
        GetConnectivityInfoResponse getConnectivityInfoResponse = GetConnectivityInfoResponse.builder()
                .connectivityInfo(Arrays.asList(connectivityInfo, connectivityInfo1)).build();
        Mockito.doReturn(getConnectivityInfoResponse).when(clientHandler).execute(any());

        GetConnectivityInfoResponse returnedGetConnectivityInfoResponse =
                dataPlaneClient.getConnectivityInfo(getConnectivityInfoRequest);
        assertThat(returnedGetConnectivityInfoResponse, is(getConnectivityInfoResponse));

        Mockito.doReturn(GetConnectivityInfoResponse.builder().build()).when(clientHandler).execute(any());
        returnedGetConnectivityInfoResponse = dataPlaneClient.getConnectivityInfo(getConnectivityInfoRequest);
        assertFalse(returnedGetConnectivityInfoResponse.hasConnectivityInfo());
    }

    @Test
    public void GIVEN_get_connectivity_request_WHEN_null_THEN_null_returned() {
        dataPlaneClient = new DataPlaneDefaultClient(SdkClientConfiguration.builder().build(), clientHandler);
        GetConnectivityInfoResponse getConnectivityInfoResponse =
                dataPlaneClient.getConnectivityInfo(null);

        assertNull(getConnectivityInfoResponse);
    }
}
