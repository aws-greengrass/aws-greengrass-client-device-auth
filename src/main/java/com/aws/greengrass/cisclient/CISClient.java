package com.aws.greengrass.cisclient;

import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import software.amazon.awssdk.services.greengrassv2data.GreengrassV2DataClient;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrassv2data.model.GetConnectivityInfoRequest;
import software.amazon.awssdk.services.greengrassv2data.model.GetConnectivityInfoResponse;
import software.amazon.awssdk.services.greengrassv2data.model.ResourceNotFoundException;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Client for retrieving connectivity info from CIS - Connectivity Info Service.
 */
public class CISClient {
    private static final Logger LOGGER = LogManager.getLogger(CISClient.class);

    private final DeviceConfiguration deviceConfiguration;
    private final GreengrassV2DataClient greengrassV2DataClient;

    private List<String> cachedHostAddresses = Collections.emptyList();

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
     * Get cached connectivity info.
     *
     * @return list of cached connectivity info items
     */
    public List<String> getCachedHostAddresses() {
        return cachedHostAddresses;
    }

    /**
     * Get connectivity info.
     *
     * @return list of connectivity info items
     */
    public List<ConnectivityInfo> getConnectivityInfo() {
        GetConnectivityInfoRequest getConnectivityInfoRequest = GetConnectivityInfoRequest.builder()
                .thingName(Coerce.toString(deviceConfiguration.getThingName())).build();
        List<ConnectivityInfo> connectivityInfoList = Collections.emptyList();

        try {
            GetConnectivityInfoResponse getConnectivityInfoResponse = greengrassV2DataClient.getConnectivityInfo(
                    getConnectivityInfoRequest);
            if (getConnectivityInfoResponse.hasConnectivityInfo()) {
                // Filter out port and metadata since it is not needed
                connectivityInfoList = getConnectivityInfoResponse.connectivityInfo();
                cachedHostAddresses = new ArrayList<>(connectivityInfoList.stream()
                        .map(ci -> ci.hostAddress())
                        .collect(Collectors.toSet()));
            }
        } catch (ValidationException | ResourceNotFoundException e) {
            LOGGER.atWarn().cause(e).log("Connectivity info doesn't exist");
        }

        return connectivityInfoList;
    }
}
