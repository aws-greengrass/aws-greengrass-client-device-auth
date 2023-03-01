/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the connectivity configuration for the plugin.
 * <p>
 * |---- configuration
 * |    |---- connectivity:
 * |         |---- hostAddresses: [...]
 * |
 * |---- runtime
 * |    |---- aggregatedHostAddresses: [...]
 * </p>
 */
public final class ConnectivityConfiguration {

    private static final String CONNECTIVITY_TOPIC = "connectivity";
    private static final String HOST_ADDRESSES_TOPIC = "hostAddresses";

    private final Topics config;
    private final Set<HostAddress> hostAddresses;

    public static ConnectivityConfiguration from(Topics configurationTopics) {
        Topics connectivityTopics = configurationTopics.lookupTopics(CONNECTIVITY_TOPIC);
        return new ConnectivityConfiguration(connectivityTopics);
    }

    private ConnectivityConfiguration(Topics config) {
        this.config = config;
        this.hostAddresses = getHostAddressesFromConfig();
    }

    public Set<HostAddress> getHostAddresses() {
        return hostAddresses;
    }

    private Set<HostAddress> getHostAddressesFromConfig() {
        return new HashSet<>(Coerce.toStringList(config.findOrDefault("", HOST_ADDRESSES_TOPIC)))
                .stream()
                .map(HostAddress::new)
                .filter(HostAddress::isValid)
                .collect(Collectors.toSet());
    }

    public boolean hasChanged(ConnectivityConfiguration config) {
        return !Objects.equals(config.getHostAddresses(), getHostAddresses());
    }
}
