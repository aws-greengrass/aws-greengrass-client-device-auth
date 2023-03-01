/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConnectivityInfoCache {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String HOST_ADDRESSES_TOPIC = "hostAddresses";
    private static final String HOST_ADDRESSES_DELIMITER = ",";

    @Setter
    private Topics runtimeTopics;

    /**
     * Cache connectivity information by source.
     *
     * @param source           source
     * @param connectivityInfo connectivity information
     */
    public void put(String source, Set<HostAddress> connectivityInfo) {
        runtimeTopics.lookupTopics(HOST_ADDRESSES_TOPIC).lookup(source)
                .withValue(connectivityInfo.stream()
                        .map(HostAddress::getHost)
                        .collect(Collectors.joining(HOST_ADDRESSES_DELIMITER)));
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

        Map<String, String> hostAddresses;
        try {
            hostAddresses = MAPPER.convertValue(hostAddressesTopics.toPOJO(),
                    new TypeReference<Map<String, String>>() {
                    });
        } catch (IllegalArgumentException e) {
            return Collections.emptySet();
        }

        return hostAddresses.values().stream()
                .filter(Utils::isNotEmpty)
                .map(addrs -> Arrays.asList(addrs.split(HOST_ADDRESSES_DELIMITER)))
                .flatMap(List::stream)
                .map(HostAddress::of)
                .collect(Collectors.toSet());
    }
}
