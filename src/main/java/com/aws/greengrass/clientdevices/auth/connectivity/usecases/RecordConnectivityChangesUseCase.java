/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.ConnectivityConfiguration;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformationSource;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.clientdevices.auth.connectivity.RecordConnectivityChangesRequest;
import com.aws.greengrass.clientdevices.auth.connectivity.RecordConnectivityChangesResponse;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Record Connectivity Changes.
 * </p>
 * This use case constructs current connectivity information from connectivity providers and returns a diff (if any)
 * since the last recorded change.
 */
public class RecordConnectivityChangesUseCase
        implements UseCases.UseCase<RecordConnectivityChangesResponse, RecordConnectivityChangesRequest> {
    private final ConnectivityInformation connectivityInformation;
    private final ConnectivityConfiguration config;

    @Inject
    public RecordConnectivityChangesUseCase(ConnectivityInformation connectivityInformation,
                                            ConnectivityConfiguration config) {
        this.connectivityInformation = connectivityInformation;
        this.config = config;
    }

    @Override
    public RecordConnectivityChangesResponse apply(RecordConnectivityChangesRequest recordChangesRequest) {
        // TODO: Consider pushing some of this logic to compute diff into the domain. Being able to retrieve
        // connectivity information for a single source may also be useful.
        Set<HostAddress> previousConnectivityInfo = connectivityInformation.getAggregatedConnectivityInformation();
        Set<HostAddress> previousConnectivityInfoConfigSource =
                connectivityInformation.getConnectivityInformationForSource(
                        ConnectivityInformationSource.CONFIGURATION);

        connectivityInformation.recordConnectivityInformationForSource(recordChangesRequest.getSource(),
                recordChangesRequest.getConnectivityInformation());

        Set<HostAddress> newConnectivityInfo = connectivityInformation.getAggregatedConnectivityInformation();
        Set<HostAddress> newConnectivityInfoConfigSource =
                connectivityInformation.getConnectivityInformationForSource(
                        ConnectivityInformationSource.CONFIGURATION);

        Set<HostAddress> addedAddresses =
                newConnectivityInfo.stream().filter((item) -> !previousConnectivityInfo.contains(item))
                        .collect(Collectors.toSet());
        Set<HostAddress> removedAddresses =
                previousConnectivityInfo.stream().filter((item) -> !newConnectivityInfo.contains(item))
                        .collect(Collectors.toSet());

        if (!Objects.equals(previousConnectivityInfoConfigSource, newConnectivityInfoConfigSource)) {
            config.setHostAddresses(newConnectivityInfoConfigSource);
        }

        return new RecordConnectivityChangesResponse(addedAddresses, removedAddresses);
    }
}
