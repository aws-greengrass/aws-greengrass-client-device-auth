/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;

import java.util.Set;
import javax.inject.Inject;

/**
 * Get aggregated connectivity information.
 */
public class GetConnectivityInformationUseCase implements UseCases.UseCase<Set<HostAddress>, Void> {
    private final ConnectivityInformation connectivityInformation;

    @Inject
    public GetConnectivityInformationUseCase(ConnectivityInformation connectivityInformation) {
        this.connectivityInformation = connectivityInformation;
    }

    @Override
    public Set<HostAddress> apply(Void unused) {
        return connectivityInformation.getAggregatedConnectivityInformation();
    }
}
