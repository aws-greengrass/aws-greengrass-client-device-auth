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
import com.aws.greengrass.clientdevices.auth.connectivity.usecases.RecordConnectivityChangesUseCase;

import javax.inject.Inject;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ConnectivityConfigurationChangedHandler implements Consumer<ConnectivityConfigurationChanged> {

    private final RecordConnectivityChangesUseCase recordConnectivityChangesUseCase;
    private final DomainEvents domainEvents;

    @Inject
    public ConnectivityConfigurationChangedHandler(RecordConnectivityChangesUseCase recordConnectivityChangesUseCase, DomainEvents domainEvents) {
        this.recordConnectivityChangesUseCase = recordConnectivityChangesUseCase;
        this.domainEvents = domainEvents;
    }

    @Override
    public void accept(ConnectivityConfigurationChanged connectivityConfigurationChanged) {
        recordConnectivityChangesUseCase.apply(
                new RecordConnectivityChangesRequest(
                        ConnectivityInformationSource.CONFIGURATION,
                        connectivityConfigurationChanged.getConfiguration().getHostAddresses().stream()
                                .map(HostAddress::new)
                                .collect(Collectors.toSet())));
    }

    public void listen() {
        domainEvents.registerListener(this, ConnectivityConfigurationChanged.class);
    }
}
