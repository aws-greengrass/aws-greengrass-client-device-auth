/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.helpers;


import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Helpers for generic assertion tooling
 */
public final class TestHelpers {
    public static final long DEFAULT_GENERIC_POLLING_TIMEOUT_MILLIS =
            TimeUnit.SECONDS.toMillis(10);

    public static final long DEFAULT_POLLING_INTERVAL_MILLIS = 500;

    private TestHelpers() {
    }

    public static boolean eventuallyTrue(Supplier<Boolean> condition) throws InterruptedException {
        return eventuallyTrue(condition, DEFAULT_GENERIC_POLLING_TIMEOUT_MILLIS, DEFAULT_POLLING_INTERVAL_MILLIS);
    }

    public static boolean eventuallyTrue(Supplier<Boolean> condition, long pollingTimeoutMillis,
                                         long pollingIntervalMillis) throws InterruptedException {
        final Instant tryUntil = Instant.now().plus(Duration.ofMillis(pollingTimeoutMillis));

        while (Instant.now().isBefore(tryUntil)) {
            if (condition.get()) {
                return true;
            }

            Thread.sleep(pollingIntervalMillis);
        }

       throw new AssertionError("Condition was not eventually true");
    }
}
