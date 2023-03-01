/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfigurationReference;

import javax.inject.Inject;

public class CacheConnectivityInformationUseCase implements UseCases.UseCase<Void, Void> {

    private final GetConnectivityInformationUseCase getConnectivityInformationUseCase;
    private final CDAConfigurationReference config;

    @Inject
    public CacheConnectivityInformationUseCase(GetConnectivityInformationUseCase getConnectivityInformationUseCase,
                                               CDAConfigurationReference config) {
        this.getConnectivityInformationUseCase = getConnectivityInformationUseCase;
        this.config = config;
    }

    @Override
    public Void apply(Void unused) {
        config.get().getRuntime().updateAggregatedHostAddresses(getConnectivityInformationUseCase.apply(null));
        return null;
    }
}
