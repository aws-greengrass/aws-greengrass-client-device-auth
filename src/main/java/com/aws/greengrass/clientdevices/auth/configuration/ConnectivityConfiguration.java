/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.clientdevices.auth.connectivity.HostAddress;
import com.aws.greengrass.config.Topics;

import java.util.Objects;
import java.util.Set;

/**
 * Manages the connectivity configuration for the plugin.
 * <p>
 * |---- configuration
 * |    |---- connectivity:
 * |         |---- hostAddresses: [...]
 * </p>
 */
public class ConnectivityConfiguration {

    private final Topics config;

    public static ConnectivityConfiguration from(Topics runtimeTopics) {
        return new ConnectivityConfiguration(runtimeTopics);
    }

    private ConnectivityConfiguration(Topics config) {
        this.config = config;
    }

    public Set<HostAddress> getHostAddresses() {
        return null; // TODO
    }

    public void setHostAddresses(Set<HostAddress> addresses) {
        // TODO
    }

    public boolean hasChanged(ConnectivityConfiguration config) {
        return !Objects.equals(config.getHostAddresses(), getHostAddresses());
    }
}
