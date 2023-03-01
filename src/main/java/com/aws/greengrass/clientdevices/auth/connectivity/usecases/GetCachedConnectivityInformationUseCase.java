/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.configuration.CDAConfigurationReference;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;

import java.util.Set;
import javax.inject.Inject;

public class GetCachedConnectivityInformationUseCase implements UseCases.UseCase<Set<HostAddress>, Void> {

    private final CDAConfigurationReference config;

    @Inject
    public GetCachedConnectivityInformationUseCase(CDAConfigurationReference config) {
        this.config = config;
    }

    @Override
    public Set<HostAddress> apply(Void unused) {
        return config.get().getRuntime().getAggregatedHostAddresses();
    }
}
