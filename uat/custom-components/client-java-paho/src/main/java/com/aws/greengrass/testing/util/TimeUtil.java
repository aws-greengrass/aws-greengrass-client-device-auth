/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.util;

public final class TimeUtil {
    private TimeUtil() {
    }

    /**
     * convert seconds to milliseconds.
     *
     * @param seconds seconds
     */
    public static long secondToMls(long seconds) {
        return seconds * 1000L;
    }

    /**
     * convert seconds to milliseconds.
     *
     * @param seconds seconds
     */
    public static int secondToMls(int seconds) {
        return seconds * 1000;
    }
}
