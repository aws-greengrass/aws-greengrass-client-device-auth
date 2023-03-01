/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.config.UpdateBehaviorTree;
import lombok.Setter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConnectivityInfoCache {
    private static final String HOST_ADDRESSES_TOPIC = "hostAddresses";
    private static final UpdateBehaviorTree HOST_ADDRESSES_UPDATE_BEHAVIOR =
            new UpdateBehaviorTree(UpdateBehaviorTree.UpdateBehavior.REPLACE, System.currentTimeMillis());

    @Setter
    private Topics runtimeTopics;

    /**
     * Cache connectivity information by source.
     *
     * @param source           source
     * @param connectivityInfo connectivity information
     */
    public void put(String source, Set<HostAddress> connectivityInfo) {
        Map<String, Object> hostAddressesToMerge = new HashMap<>();
        hostAddressesToMerge.put(
                source,
                connectivityInfo.stream().map(HostAddress::getHost).collect(Collectors.toList()));
        runtimeTopics.lookupTopics(HOST_ADDRESSES_TOPIC, source)
                .updateFromMap(hostAddressesToMerge, HOST_ADDRESSES_UPDATE_BEHAVIOR);
    }

    /**
     * Get aggregated connectivity information from cache.
     *
     * @return connectivity information
     */
    public Set<HostAddress> getAll() {
        Topics hostAddressesTopics = runtimeTopics.lookupTopics(HOST_ADDRESSES_TOPIC);
        if (hostAddressesTopics == null) {
            return Collections.emptySet();
        }

        Set<HostAddress> connectivityInfo = new HashSet<>();
        for (Object addrsBySource : hostAddressesTopics.toPOJO().values()) {
            if (!(addrsBySource instanceof Map)) {
                continue;
            }
            for (Object addrs : ((Map<?,?>) addrsBySource).values()) {
                if (!(addrs instanceof Collection)) {
                    continue;
                }
                for (Object addr : (Collection<?>) addrs) {
                    if (!(addr instanceof String)) {
                        continue;
                    }
                    connectivityInfo.add(HostAddress.of((String) addr));
                }
            }
        }
        return connectivityInfo;
    }
}
