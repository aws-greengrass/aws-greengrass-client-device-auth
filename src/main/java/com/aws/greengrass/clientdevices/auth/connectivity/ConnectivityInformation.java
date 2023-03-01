/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import com.aws.greengrass.deployment.DeviceConfiguration;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.Coerce;
import com.aws.greengrass.util.GreengrassServiceClientFactory;
import software.amazon.awssdk.services.greengrassv2data.model.ConnectivityInfo;
import software.amazon.awssdk.services.greengrassv2data.model.GetConnectivityInfoRequest;
import software.amazon.awssdk.services.greengrassv2data.model.GetConnectivityInfoResponse;
import software.amazon.awssdk.services.greengrassv2data.model.ResourceNotFoundException;
import software.amazon.awssdk.services.greengrassv2data.model.ValidationException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Retrieving connectivity info from CIS - Connectivity Info Service.
 */
public class ConnectivityInformation {
    private static final Logger LOGGER = LogManager.getLogger(ConnectivityInformation.class);

    private final DeviceConfiguration deviceConfiguration;
    private final GreengrassServiceClientFactory clientFactory;
    private final Map<ConnectivityInformationSource, Set<HostAddress>> connectivityInformationMap
            = new ConcurrentHashMap<>();


    /**
     * Constructor.
     *
     * @param deviceConfiguration client to get the device details
     * @param clientFactory       factory to get data plane client
     */
    @Inject
    public ConnectivityInformation(DeviceConfiguration deviceConfiguration,
                                   GreengrassServiceClientFactory clientFactory) {
        this.deviceConfiguration = deviceConfiguration;
        this.clientFactory = clientFactory;
    }

    /**
     * Get connectivity info.
     *
     * @return list of connectivity info items
     */
    public List<ConnectivityInfo> getConnectivityInfo() {
        GetConnectivityInfoRequest getConnectivityInfoRequest =
                GetConnectivityInfoRequest.builder().thingName(Coerce.toString(deviceConfiguration.getThingName()))
                        .build();
        List<ConnectivityInfo> connectivityInfoList = Collections.emptyList();

        try {
            GetConnectivityInfoResponse getConnectivityInfoResponse =
                    clientFactory.getGreengrassV2DataClient().getConnectivityInfo(getConnectivityInfoRequest);
            if (getConnectivityInfoResponse.hasConnectivityInfo()) {
                // Filter out port and metadata since it is not needed
                connectivityInfoList = getConnectivityInfoResponse.connectivityInfo();
            }
        } catch (ValidationException | ResourceNotFoundException e) {
            LOGGER.atWarn().cause(e).log("Connectivity info doesn't exist");
        }

        return connectivityInfoList;
    }

    /**
     * Get connectivity information.
     *
     * @return set of connectivity information from all connectivity sources.
     */
    public Set<HostAddress> getAggregatedConnectivityInformation() {
        return connectivityInformationMap.values().stream().flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Get connectivity information for a single source.
     *
     * @param source connectivity information source
     * @return set of connectivity information from the given source
     */
    public Set<HostAddress> getConnectivityInformationForSource(ConnectivityInformationSource source) {
        return connectivityInformationMap.getOrDefault(source, Collections.emptySet());
    }

    /**
     * Record connectivity information.
     *
     * @param source                 connectivity information source.
     * @param sourceConnectivityInfo connectivity information.
     */
    public void recordConnectivityInformationForSource(ConnectivityInformationSource source,
                                                       Set<HostAddress> sourceConnectivityInfo) {
        connectivityInformationMap.compute(source, (k,v) -> {
            if (!Objects.equals(v, sourceConnectivityInfo)) {
                LOGGER.atInfo().kv("source", source).kv("connectivityInformation", sourceConnectivityInfo)
                        .log("Updating connectivity information");
            }
            return sourceConnectivityInfo;
        });
    }
}
