/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import com.aws.greengrass.clientdevices.auth.configuration.RuntimeConfiguration;
import lombok.Setter;

import java.util.Set;

public class ConnectivityInfoCache {

    @Setter
    private RuntimeConfiguration runtimeConfiguration;

    /**
     * Cache connectivity information by source.
     *
     * @param source           source
     * @param connectivityInfo connectivity information
     */
    public void put(String source, Set<HostAddress> connectivityInfo) {
        runtimeConfiguration.putHostAddressForSource(source, connectivityInfo);
    }

    /**
     * Get aggregated connectivity information from cache.
     *
     * @return connectivity information
     */
    public Set<HostAddress> getAll() {
        return runtimeConfiguration.getAggregatedHostAddresses();
    }
}
