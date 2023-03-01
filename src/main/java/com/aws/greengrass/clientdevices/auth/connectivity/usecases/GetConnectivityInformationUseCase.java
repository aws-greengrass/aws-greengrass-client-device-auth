/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.usecases;

import com.aws.greengrass.clientdevices.auth.api.UseCases;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformation;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformationSource;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.clientdevices.auth.infra.NetworkStateProvider;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Get aggregated connectivity information.
 */
public class GetConnectivityInformationUseCase implements
        UseCases.UseCase<Set<HostAddress>, ConnectivityInformationSource> {
    private final ConnectivityInformation connectivityInformation;
    private final NetworkStateProvider networkStateProvider;
    private final UseCases useCases;


    // TODO stop-gap for transition away from ConnectivityInfo::getCachedHostAddresses
    public static final Function<GetConnectivityInformationUseCase, Supplier<List<String>>>
            LEGACY_GET_CACHED_HOST_ADDRESSES = useCase -> () ->
            useCase.apply(null).stream()
                    .map(HostAddress::getHost)
                    .collect(Collectors.toList());

    /**
     * Construct a new GetConnectivityInformationUseCase.
     *
     * @param connectivityInformation connectivity information
     * @param networkStateProvider    network state provider
     * @param useCases                use cases
     */
    @Inject
    public GetConnectivityInformationUseCase(ConnectivityInformation connectivityInformation,
                                             NetworkStateProvider networkStateProvider,
                                             UseCases useCases) {
        this.connectivityInformation = connectivityInformation;
        this.networkStateProvider = networkStateProvider;
        this.useCases = useCases;
    }

    @Override
    public Set<HostAddress> apply(ConnectivityInformationSource source) {
        if (source == null) {
            Set<HostAddress> hostAddresses = connectivityInformation.getAggregatedConnectivityInformation();
            if (networkStateProvider.getConnectionState() == NetworkStateProvider.ConnectionState.NETWORK_DOWN) {
                hostAddresses.addAll(useCases.get(GetCachedConnectivityInformationUseCase.class).apply(null));
            }
            return hostAddresses;
        } else {
            // TODO: cache by source? currently this is only called for ConnectivityInformationSource.CONFIGURATION
            return connectivityInformation.getConnectivityInformationForSource(source);
        }
    }
}
