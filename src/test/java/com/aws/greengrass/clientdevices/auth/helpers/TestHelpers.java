/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.helpers;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Helpers for generic assertion tooling
 */
public final class TestHelpers {
    public static final long DEFAULT_GENERIC_POLLING_TIMEOUT_MILLIS =
            TimeUnit.SECONDS.toMillis(10);

    private TestHelpers() {
    }

    public static boolean eventuallyTrue(Supplier<Boolean> condition, long... optional) throws InterruptedException {
        long timeoutInMillis = optional.length >= 1 ? optional[0] : DEFAULT_GENERIC_POLLING_TIMEOUT_MILLIS;
        long pollingIntervalInMillis = optional.length >= 2 ? optional[1] : 500;
        final long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) < timeoutInMillis) {
            if (condition.get()) {
                return true;
            }
            Thread.sleep(pollingIntervalInMillis);
        }
        return false;
    }
}
