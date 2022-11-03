/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.clientdevices.auth.connectivity.RecordConnectivityChangesRequest;
import com.aws.greengrass.clientdevices.auth.connectivity.RecordConnectivityChangesResponse;

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

    @Inject
    public RecordConnectivityChangesUseCase(ConnectivityInformation connectivityInformation) {
        this.connectivityInformation = connectivityInformation;
    }

    @Override
    public RecordConnectivityChangesResponse apply(RecordConnectivityChangesRequest recordChangesRequest) {
        // TODO: Consider pushing some of this logic to compute diff into the domain. Being able to retrieve
        // connectivity information for a single source may also be useful.
        Set<HostAddress> previousConnectivityInfo = connectivityInformation.getAggregatedConnectivityInformation();

        connectivityInformation.recordConnectivityInformationForSource(recordChangesRequest.getSource(),
                recordChangesRequest.getConnectivityInformation());

        Set<HostAddress> newConnectivityInfo = connectivityInformation.getAggregatedConnectivityInformation();
        Set<HostAddress> addedAddresses =
                newConnectivityInfo.stream().filter((item) -> !previousConnectivityInfo.contains(item))
                        .collect(Collectors.toSet());
        Set<HostAddress> removedAddresses =
                previousConnectivityInfo.stream().filter((item) -> !newConnectivityInfo.contains(item))
                        .collect(Collectors.toSet());

        return new RecordConnectivityChangesResponse(addedAddresses, removedAddresses);
    }
}
