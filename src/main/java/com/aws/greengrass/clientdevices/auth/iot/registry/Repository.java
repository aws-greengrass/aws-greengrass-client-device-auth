/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

/**
 * Mediates between the registry domain and data storage layer
 * using a collection-like interface for accessing domain objects.
 *
 * @param <K> Registry key type
 * @param <V> Registry value type
 */
public interface Repository<K, V> {
    /**
     * Puts a key-value pair into the repository.
     *
     * @param k Key object
     * @param v Value object
     */
    void put(K k, V v);

    /**
     * Retrieves the value for the given key from the repository.
     *
     * @param k Key object
     * @return associated value for the key
     */
    V get(K k);

    /**
     * Removes the entry for the given key from the repository.
     *
     * @param k Key object
     */
    void remove(K k);
}
