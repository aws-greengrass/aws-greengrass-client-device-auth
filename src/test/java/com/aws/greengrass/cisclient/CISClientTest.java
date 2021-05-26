package com.aws.greengrass.cisclient;

import com.aws.greengrass.config.Topic;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrassv2data.model.GetConnectivityInfoRequest;
import software.amazon.awssdk.services.greengrassv2data.model.GetConnectivityInfoResponse;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;

import java.util.Arrays;
import java.util.List;

import static com.aws.greengrass.deployment.DeviceConfiguration.DEVICE_PARAM_THING_NAME;
import static com.aws.greengrass.testcommons.testutilities.ExceptionLogProtector.ignoreExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith({MockitoExtension.class, GGExtension.class})
public class CISClientTest {

    private CISClient cisClient;
    @Mock
    private DeviceConfiguration deviceConfiguration;

    @Mock
    private GreengrassV2DataClient greengrassV2DataClient;

    @Mock
    private GreengrassServiceClientFactory clientFactory;

    @Mock
    protected Context context;

    @BeforeEach
    public void setup() {
        Topic thingNameTopic = Topic.of(context, DEVICE_PARAM_THING_NAME, "testThing");
        lenient().doReturn(thingNameTopic).when(deviceConfiguration).getThingName();
        lenient().when(clientFactory.getGreengrassV2DataClient()).thenReturn(greengrassV2DataClient);
        cisClient = new CISClient(deviceConfiguration, clientFactory);
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    void GIVEN_connectivity_info_WHEN_get_connectivity_info_THEN_connectivity_info_returned() {
        ConnectivityInfo connectivityInfo = ConnectivityInfo.builder().hostAddress("172.8.8.10")
                .metadata("").id("172.8.8.10").portNumber(8883).build();
        ConnectivityInfo connectivityInfo1 = ConnectivityInfo.builder().hostAddress("localhost")
                .metadata("").id("localhost").portNumber(8883).build();
        GetConnectivityInfoResponse getConnectivityInfoResponse = GetConnectivityInfoResponse.builder()
                .connectivityInfo(Arrays.asList(connectivityInfo, connectivityInfo1)).build();
        doReturn(getConnectivityInfoResponse).when(greengrassV2DataClient)
                .getConnectivityInfo(any(GetConnectivityInfoRequest.class));

        List<ConnectivityInfo> connectivityInfos = cisClient.getConnectivityInfo();
        verify(greengrassV2DataClient, times(1))
                .getConnectivityInfo(any(GetConnectivityInfoRequest.class));
        assertThat(connectivityInfos, containsInAnyOrder(connectivityInfo, connectivityInfo1));
    }

    @Test
    void GIVEN_no_connectivity_info_WHEN_get_connectivity_info_THEN_no_connectivity_info_returned() {
        GetConnectivityInfoResponse getConnectivityInfoResponse = GetConnectivityInfoResponse.builder().build();
        doReturn(getConnectivityInfoResponse).when(greengrassV2DataClient)
                .getConnectivityInfo(any(GetConnectivityInfoRequest.class));

        List<ConnectivityInfo> connectivityInfos = cisClient.getConnectivityInfo();
        verify(greengrassV2DataClient, times(1))
                .getConnectivityInfo(any(GetConnectivityInfoRequest.class));
        assertThat(connectivityInfos, is(empty()));
    }

    @Test
    void GIVEN_cloudThrowValidationException_WHEN_get_connectivity_info_THEN_no_connectivity_info_returned(
            ExtensionContext context) {
        ignoreExceptionOfType(context, ValidationException.class);
        when(greengrassV2DataClient.getConnectivityInfo(any(GetConnectivityInfoRequest.class)))
                .thenThrow(ValidationException.class);

        assertThat(cisClient.getConnectivityInfo(), is(empty()));
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @Test
    void GIVEN_cached_connectivity_info_WHEN_get_cached_connectivity_info_THEN_connectivity_info_returned() {
        ConnectivityInfo connectivityInfo = ConnectivityInfo.builder().hostAddress("172.8.8.10")
                .metadata("").id("172.8.8.10").portNumber(8883).build();
        ConnectivityInfo connectivityInfo1 = ConnectivityInfo.builder().hostAddress("localhost")
                .metadata("").id("localhost").portNumber(8883).build();
        GetConnectivityInfoResponse getConnectivityInfoResponse = GetConnectivityInfoResponse.builder()
                .connectivityInfo(Arrays.asList(connectivityInfo, connectivityInfo1)).build();
        doReturn(getConnectivityInfoResponse).when(greengrassV2DataClient)
                .getConnectivityInfo(any(GetConnectivityInfoRequest.class));

        cisClient.getConnectivityInfo();
        List<String> connectivityInfos = cisClient.getCachedHostAddresses();
        assertThat(connectivityInfos, containsInAnyOrder("172.8.8.10", "localhost"));
    }
}
