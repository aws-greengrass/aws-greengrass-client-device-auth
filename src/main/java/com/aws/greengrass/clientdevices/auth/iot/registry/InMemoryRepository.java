/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mediates between the registry domain and the in-memory data storage.
 *
 * @param <K> Registry key type
 * @param <V> Registry value type
 */
public class InMemoryRepository<K, V> implements Repository<K, V> {
    // repository size bound by default registry cache size;
    // it evicts oldest written entry if the max size is reached
    private final Map<K, V> repository = Collections.synchronizedMap(
            new LinkedHashMap<K, V>(RegistryConfig.REGISTRY_CACHE_SIZE, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > RegistryConfig.REGISTRY_CACHE_SIZE;
                }
            });

    @Override
    public void put(K k, V v) {
        repository.put(k, v);
    }

    @Override
    public V get(K k) {
        return repository.get(k);
    }

    @Override
    public void remove(K k) {
        repository.remove(k);
    }
}
