/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.platforms;

import com.aws.greengrass.platforms.common.Utils;
import com.aws.greengrass.platforms.linux.LinuxPlatform;
import com.aws.greengrass.platforms.windows.WindowsPlatform;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point for {@link com.aws.greengrass.platforms} package.
 * <br>
 * <br>
 * Provides general cross-platform tools for use in the Evergreen Features
 * test framework.
 * <br>
 * <br>
 * Example usage:
 * <br>
 * <br>
 * <pre>
 *     // Queries for the direct descendants of process 24601 using the
 *     // appropriate platform api.
 *     final int[] pids = Platform.getInstance().getCmds()
 *         .getDirectDescendantsPids(24601);
 * </pre>
 */
public abstract class Platform {

    private static final AtomicReference<Platform> INSTANCE =
        new AtomicReference<>();

    protected static final AtomicReference<NetworkUtils> NETWORK_INSTANCE =
            new AtomicReference<>();

    /**
     * Gets platform instance.
     *
     * @return singleton instance of this class.
     * @throws RuntimeException if the detected platform is not supported.
     */
    public static synchronized Platform getInstance() {
        return Utils.replaceIfNull(INSTANCE, () -> {
            if (PlatformResolver.RANKS.get().containsKey("linux")) {
                return new LinuxPlatform();
            } else if (PlatformResolver.RANKS.get().containsKey("windows")) {
                return new WindowsPlatform();
            }
            throw new RuntimeException("platform not supported");
        });
    }

    /**
     * See {@link NetworkUtils}.
     *
     * @return bundle of cross-platform commands for interacting with the network.
     */
    public abstract NetworkUtils getNetworkUtils();

}
