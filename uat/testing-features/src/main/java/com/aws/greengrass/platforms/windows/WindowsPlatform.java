/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.platforms.windows;

import com.aws.greengrass.platforms.NetworkUtils;
import com.aws.greengrass.platforms.Platform;
import com.aws.greengrass.platforms.common.Utils;

/**
 * Implementation of {@link Platform} for Windows.
 */
public class WindowsPlatform extends Platform {

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized NetworkUtils getNetworkUtils() {
        return Utils.replaceIfNull(NETWORK_INSTANCE, NetworkUtilsWindows::new);
    }
}
