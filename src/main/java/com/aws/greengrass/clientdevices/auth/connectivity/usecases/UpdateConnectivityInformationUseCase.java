/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.connectivity.UpdateConnectivityInformationRequest;

import javax.inject.Inject;

/**
 * Update Connectivity Information.
 * </p>
 * This use case constructs current connectivity information from
 * connectivity providers and returns a diff (if any) since the
 * last update.
 */
public class UpdateConnectivityInformationUseCase implements
        UseCases.UseCase<Void, UpdateConnectivityInformationRequest> {
    private final ConnectivityInformation connectivityInformation;

    @Inject
    public UpdateConnectivityInformationUseCase(ConnectivityInformation connectivityInformation) {
        this.connectivityInformation = connectivityInformation;
    }

    @Override
    public Void execute(UpdateConnectivityInformationRequest updateRequest) {
        connectivityInformation.updateConnectivityInformationForSource(
                updateRequest.getSource(), updateRequest.getConnectivityInformation());
        return null;
    }
}
