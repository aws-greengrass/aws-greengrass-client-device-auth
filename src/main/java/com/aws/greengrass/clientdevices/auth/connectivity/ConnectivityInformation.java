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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    // TODO: Legacy structure to be removed later
    protected volatile List<String> cachedHostAddresses = Collections.emptyList();

    private final Map<String, Set<HostAddress>> connectivityInformationMap = new ConcurrentHashMap<>();


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
            GetConnectivityInfoResponse getConnectivityInfoResponse = clientFactory.getGreengrassV2DataClient()
                    .getConnectivityInfo(getConnectivityInfoRequest);
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

        // NOTE: Eventually this code will move into infrastructure and connectivity information
        // will be updated as part of UpdateConnectivityInformationUseCase. That migration can happen
        // in phases.
        // Phase 1) Call updateConnectivityInformationForSource here so that GetConnectivityInformationUseCase
        //   returns the expected data after we receive a CIS update.
        // Phase 2) Introduce new certificate rotation workflows that use GetConnectivityInformationUseCase
        //   instead of getCachedHostAddresses(). This will decouple cert rotation and connectivity info domains.
        // Phase 3) Remove this code entirely. CISShadowMonitor will update via UpdateConnectivityInformationUseCase.
        Set<HostAddress> hostAddresses = connectivityInfoList.stream()
                .map(HostAddress::of)
                .collect(Collectors.toSet());
        recordConnectivityInformationForSource("connectivity-information-service", hostAddresses);

        return connectivityInfoList;
    }

    /**
     * Get connectivity information.
     *
     * @return set of connectivity information from all connectivity sources.
     */
    public Set<HostAddress> getAggregatedConnectivityInformation() {
        return connectivityInformationMap.entrySet()
                .stream()
                .map(Map.Entry::getValue)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Record connectivity information.
     *
     * @param source                 connectivity information source.
     * @param sourceConnectivityInfo connectivity information.
     */
    public void recordConnectivityInformationForSource(String source, Set<HostAddress> sourceConnectivityInfo) {
        LOGGER.atInfo()
                .kv("source", source)
                .kv("connectivityInformation", sourceConnectivityInfo)
                .log("Updating connectivity information");
        connectivityInformationMap.put(source, sourceConnectivityInfo);
    }
}
