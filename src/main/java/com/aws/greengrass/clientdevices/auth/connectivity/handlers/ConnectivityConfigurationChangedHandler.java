/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity.handlers;

import com.aws.greengrass.clientdevices.auth.api.DomainEvents;
import com.aws.greengrass.clientdevices.auth.configuration.events.ConnectivityConfigurationChanged;
import com.aws.greengrass.clientdevices.auth.connectivity.ConnectivityInformationSource;
import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.clientdevices.auth.connectivity.RecordConnectivityChangesRequest;
import com.aws.greengrass.clientdevices.auth.connectivity.usecases.GetConnectivityInformationUseCase;
import com.aws.greengrass.clientdevices.auth.connectivity.usecases.RecordConnectivityChangesUseCase;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class ConnectivityConfigurationChangedHandler implements Consumer<ConnectivityConfigurationChanged> {

    private final GetConnectivityInformationUseCase getConnectivityInformationUseCase;
    private final RecordConnectivityChangesUseCase recordConnectivityChangesUseCase;
    private final DomainEvents domainEvents;

    /**
     * Construct a ConnectivityConfigurationChangedHandler.
     *
     * @param getConnectivityInformationUseCase get connectivity information use case
     * @param recordConnectivityChangesUseCase  record connectivity information use case
     * @param domainEvents                      domain events
     */
    @Inject
    public ConnectivityConfigurationChangedHandler(GetConnectivityInformationUseCase getConnectivityInformationUseCase,
                                                   RecordConnectivityChangesUseCase recordConnectivityChangesUseCase,
                                                   DomainEvents domainEvents) {
        this.getConnectivityInformationUseCase = getConnectivityInformationUseCase;
        this.recordConnectivityChangesUseCase = recordConnectivityChangesUseCase;
        this.domainEvents = domainEvents;
    }

    @Override
    public void accept(ConnectivityConfigurationChanged connectivityConfigurationChanged) {
        Set<HostAddress> existingConnectivityInfo =
                getConnectivityInformationUseCase.apply(ConnectivityInformationSource.CONFIGURATION);

        Set<HostAddress> newConnectivityInfo =
                connectivityConfigurationChanged.getConfiguration().getHostAddresses().stream()
                        .map(HostAddress::new)
                        .collect(Collectors.toSet());

        if (!Objects.equals(existingConnectivityInfo, newConnectivityInfo)) {
            recordConnectivityChangesUseCase.apply(
                    new RecordConnectivityChangesRequest(
                            ConnectivityInformationSource.CONFIGURATION, newConnectivityInfo));
        }
    }

    public void listen() {
        domainEvents.registerListener(this, ConnectivityConfigurationChanged.class);
    }
}
