/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

import java.time.Instant;

public interface RefreshableRegistryEntry {
    /**
     * Provides time instant until which the entry is valid.
     * @return time instant
     */
    Instant getValidTill();

    /**
     * Set the time instant until which the entry is valid.
     * @param validTill time instant
     */
    void setValidTill(Instant validTill);

    /**
     * Indicates whether the registry entry is valid by checking its TTL.
     * @return a boolean indicator
     */
    default boolean isValid() {
        return getValidTill().isAfter(Instant.now());
    }
}
