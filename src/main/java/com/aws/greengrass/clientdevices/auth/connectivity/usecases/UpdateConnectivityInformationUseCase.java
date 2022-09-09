/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.clientdevices.auth.connectivity.UpdateConnectivityInformationRequest;
import com.aws.greengrass.clientdevices.auth.connectivity.UpdateConnectivityInformationResponse;

import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Update Connectivity Information.
 * </p>
 * This use case constructs current connectivity information from
 * connectivity providers and returns a diff (if any) since the
 * last update.
 */
public class UpdateConnectivityInformationUseCase implements
        UseCases.UseCase<UpdateConnectivityInformationResponse, UpdateConnectivityInformationRequest, Exception> {
    private final ConnectivityInformation connectivityInformation;

    @Inject
    public UpdateConnectivityInformationUseCase(ConnectivityInformation connectivityInformation) {
        this.connectivityInformation = connectivityInformation;
    }

    @Override
    public UpdateConnectivityInformationResponse apply(UpdateConnectivityInformationRequest updateRequest) {
        Set<HostAddress> previousConnectivityInfo = connectivityInformation.getAggregatedConnectivityInformation();

        connectivityInformation.updateConnectivityInformationForSource(
                updateRequest.getSource(), updateRequest.getConnectivityInformation());

        Set<HostAddress> newConnectivityInfo = connectivityInformation.getAggregatedConnectivityInformation();
        Set<HostAddress> addedAddresses = newConnectivityInfo.stream()
                .filter((item) -> !previousConnectivityInfo.contains(item))
                .collect(Collectors.toSet());
        Set<HostAddress> removedAddresses = previousConnectivityInfo.stream()
                .filter((item) -> !newConnectivityInfo.contains(item))
                .collect(Collectors.toSet());

        return new UpdateConnectivityInformationResponse(addedAddresses, removedAddresses);
    }
}
