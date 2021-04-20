package com.aws.greengrass.dcmclient;

import com.aws.greengrass.config.Topic;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.utils.TestConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.greengrass.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrass.model.GetConnectivityInfoResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.aws.greengrass.deployment.DeviceConfiguration.DEVICE_PARAM_THING_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@ExtendWith({MockitoExtension.class})
public class ClientTest {
    private Client client;
    @Mock
    private DeviceConfiguration deviceConfiguration;

    @Mock
    private DataPlaneClient greengrassClient;

    @Mock
    protected Context context;

    @BeforeEach
    public void setup() {
        Topic thingNameTopic = Topic.of(context, DEVICE_PARAM_THING_NAME, "testThing");
        lenient().doReturn(thingNameTopic).when(deviceConfiguration).getThingName();
        client = new Client(deviceConfiguration, greengrassClient);
    }

    @Test
    public void GIVEN_connectivity_info_WHEN_sent_THEN_update_connectivity_info_called() {
        ConnectivityInfo connectivityInfo = ConnectivityInfo.builder().hostAddress(TestConstants.IP_2)
                .portNumber(TestConstants.PORT).metadata("").build();
        List<ConnectivityInfo> connectivityInfoItems = new ArrayList<>();
        connectivityInfoItems.add(connectivityInfo);
        client.updateConnectivityInfo(connectivityInfoItems);

        verify(greengrassClient, times(1)).updateConnectivityInfo(any());
    }

    @Test
    public void GIVEN_connectivity_info_WHEN_null_THEN_update_connectivity_info_not_called() {
        client.updateConnectivityInfo(null);
        verify(greengrassClient, times(0)).updateConnectivityInfo(any());

        client.updateConnectivityInfo(new ArrayList<>());
        verify(greengrassClient, times(0)).updateConnectivityInfo(any());
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    public void GIVEN_connectivity_info_WHEN_get_connectivity_info_THEN_connectivity_info_returned()
            throws ClientException {
        ConnectivityInfo connectivityInfo = ConnectivityInfo.builder().hostAddress("172.8.8.10")
                .metadata("").id("172.8.8.10").portNumber(8883).build();
        ConnectivityInfo connectivityInfo1 = ConnectivityInfo.builder().hostAddress("localhost")
                .metadata("").id("localhost").portNumber(8883).build();
        GetConnectivityInfoResponse getConnectivityInfoResponse = GetConnectivityInfoResponse.builder()
                .connectivityInfo(Arrays.asList(connectivityInfo, connectivityInfo1)).build();
        doReturn(getConnectivityInfoResponse).when(greengrassClient).getConnectivityInfo(any());

        List<ConnectivityInfo> connectivityInfos = client.getConnectivityInfo();
        verify(greengrassClient, times(1)).getConnectivityInfo(any());
        assertThat(connectivityInfos, containsInAnyOrder(connectivityInfo, connectivityInfo1));
    }

    @Test
    public void GIVEN_no_connectivity_info_WHEN_get_connectivity_info_THEN_no_connectivity_info_returned()
            throws ClientException {
        GetConnectivityInfoResponse getConnectivityInfoResponse = GetConnectivityInfoResponse.builder().build();
        doReturn(getConnectivityInfoResponse).when(greengrassClient).getConnectivityInfo(any());

        List<ConnectivityInfo> connectivityInfos = client.getConnectivityInfo();
        verify(greengrassClient, times(1)).getConnectivityInfo(any());
        assertThat(connectivityInfos, is(empty()));
    }
}
