/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Get aggregated connectivity information.
 */
public class GetConnectivityInformationUseCase implements UseCases.UseCase<Set<HostAddress>, Void> {
    private final ConnectivityInformation connectivityInformation;

    // TODO stop-gap for transition away from ConnectivityInfo::getCachedHostAddresses
    public static final Function<GetConnectivityInformationUseCase, Supplier<List<String>>>
            LEGACY_GET_CACHED_HOST_ADDRESSES = useCase -> () ->
            useCase.apply(null).stream()
                    .map(HostAddress::getHost)
                    .collect(Collectors.toList());

    @Inject
    public GetConnectivityInformationUseCase(ConnectivityInformation connectivityInformation) {
        this.connectivityInformation = connectivityInformation;
    }

    @Override
    public Set<HostAddress> apply(Void unused) {
        return connectivityInformation.getAggregatedConnectivityInformation();
    }
}
