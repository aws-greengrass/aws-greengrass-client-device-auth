/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.connectivity;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Utils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aws.greengrass.lifecyclemanager.GreengrassService.RUNTIME_STORE_NAMESPACE_TOPIC;

public class ConnectivityInfoCache {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String HOST_ADDRESSES_TOPIC = "hostAddresses";

    private final Topics runtimeTopics;

    @Inject
    public ConnectivityInfoCache(Topics topics) {
        this.runtimeTopics = topics.lookupTopics(RUNTIME_STORE_NAMESPACE_TOPIC);
    }

    public void put(String source, Set<HostAddress> connectivityInfo) {
        runtimeTopics.lookupTopics(HOST_ADDRESSES_TOPIC).lookup(source)
                .withValue(connectivityInfo.stream()
                        .map(HostAddress::getHost)
                        .collect(Collectors.joining(",")));
    }

    public Set<HostAddress> getAll() {
        Topics hostAddressesTopics = runtimeTopics.lookupTopics(HOST_ADDRESSES_TOPIC);
        if (hostAddressesTopics == null) {
            return Collections.emptySet();
        }

        Map<String, String> hostAddresses;
        try {
            hostAddresses = MAPPER.convertValue(hostAddressesTopics.toPOJO(),
                    new TypeReference<Map<String, String>>(){});
        } catch (IllegalArgumentException e) {
            return Collections.emptySet();
        }

        return hostAddresses.values().stream()
                .filter(Utils::isNotEmpty)
                .map(addrs -> Arrays.asList(addrs.split(",")))
                .flatMap(List::stream)
                .map(HostAddress::of)
                .collect(Collectors.toSet());
    }
}
