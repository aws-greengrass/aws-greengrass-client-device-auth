/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

public class RegistryConfig {
    protected static final int REGISTRY_CACHE_SIZE = 50;
    protected static final int REGISTRY_CACHE_ENTRY_TTL_SECONDS = 24 * 60 * 60; // 1 day
    protected static final int REGISTRY_REFRESH_FREQUENCY_SECONDS = 24 * 60 * 60; // 1 day
}
