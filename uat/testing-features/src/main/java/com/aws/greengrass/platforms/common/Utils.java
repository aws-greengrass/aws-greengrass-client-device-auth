/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.platforms.common;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Miscellaneous standalone functions for general usage.
 */
public final class Utils {

    /**
     * Private constructor to prevent instantiation.
     */
    private Utils() {
    }

    /**
     * Replace atomic reference by value if null.
     * 
     * @param atom an AtomicReference with nullable value
     * @param getNewValue a supplier to be called if the AtomicReference's
     *                    value is null.
     * @param <T> any nullable type
     * @return the updated value in the AtomicReference
     */
    public static <T> T replaceIfNull(
        final AtomicReference<T> atom, final Supplier<T> getNewValue
    ) {
        return atom.updateAndGet(
            value -> Optional.ofNullable(value).orElseGet(getNewValue)
        );
    }
}
