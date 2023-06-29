/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.platforms;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Used by {@link Platform} to decide what platform is being tested.
 */
public final class PlatformResolver {
    // This needs to come before RANKS so that IS_WINDOWS is declared and set
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("wind");

    public static final AtomicReference<Map<String, Integer>> RANKS =
            new AtomicReference<>(Collections.unmodifiableMap(initializeRanks()));

    private PlatformResolver() {
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    private static Map<String, Integer> initializeRanks() {
        Map<String, Integer> ranks = new HashMap<>();
        // figure out what OS we're running and add applicable tags
        // The more specific a tag is, the higher its rank should be
        // TODO: use better way to determine if a field is platform specific. Eg: using 'platform$' prefix.
        ranks.put("all", 0);
        ranks.put("any", 0);

        if (Files.exists(Paths.get("/proc"))) {
            ranks.put("linux", 10);
        }
        if (IS_WINDOWS) {
            ranks.put("windows", 5);
        }
        return ranks;
    }
}
