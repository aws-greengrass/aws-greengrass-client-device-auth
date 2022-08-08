/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.registry;

public interface RefreshableRegistry {
    /**
     * Refresh registry entries.
     * 1. sync registry entries from the cloud
     * 2. remove invalid/stale registry entries
     */
    void refresh();
}
