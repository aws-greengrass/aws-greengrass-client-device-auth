/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

public class RegistryConfig {
    // TODO: make these configurable
    public static final long REGISTRY_ENTRY_TTL_SECONDS = 24L * 60L * 60L;  // 1 day
    public static final long REGISTRY_REFRESH_FREQUENCY_SECONDS = REGISTRY_ENTRY_TTL_SECONDS;
    public static final int REGISTRY_CACHE_SIZE = 2500;
}
