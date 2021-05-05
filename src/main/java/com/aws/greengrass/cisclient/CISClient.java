package com.aws.greengrass.cisclient;

import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrassv2data.model.GetConnectivityInfoRequest;
import software.amazon.awssdk.services.greengrassv2data.model.GetConnectivityInfoResponse;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

public class CISClient {
    private final DeviceConfiguration deviceConfiguration;
    private final GreengrassV2DataClient greengrassV2DataClient;

    /**
     * Constructor.
     *
     * @param deviceConfiguration client to get the device details
     * @param clientFactory       factory to get data plane client
     */
    @Inject
    public CISClient(DeviceConfiguration deviceConfiguration, GreengrassServiceClientFactory clientFactory) {
        this.deviceConfiguration = deviceConfiguration;
        this.greengrassV2DataClient = clientFactory.getGreengrassV2DataClient();
    }

    /**
     * Get connectivity info.
     *
     * @return list of connectivity info items
     * @throws CISClientException CISClientException
     */
    public List<ConnectivityInfo> getConnectivityInfo() throws CISClientException {
        GetConnectivityInfoRequest getConnectivityInfoRequest = GetConnectivityInfoRequest.builder()
                .thingName(Coerce.toString(deviceConfiguration.getThingName())).build();

        try {
            GetConnectivityInfoResponse getConnectivityInfoResponse = greengrassV2DataClient.getConnectivityInfo(
                    getConnectivityInfoRequest);
            if (getConnectivityInfoResponse.hasConnectivityInfo()) {
                return getConnectivityInfoResponse.connectivityInfo();
            } else {
                return Collections.emptyList();
            }
        } catch (AwsServiceException | SdkClientException e) {
            throw new CISClientException(e);
        }
    }
}
