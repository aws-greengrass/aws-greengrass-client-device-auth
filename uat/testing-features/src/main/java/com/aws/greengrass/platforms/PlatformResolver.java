/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.platforms;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used by {@link Platform} to decide what platform is being tested.
 */
@Log4j2
public final class PlatformResolver {
    private static final Set<String> SUPPORTED_PLATFORMS = Collections.unmodifiableSet(Stream.of(
            "all", "any", "unix", "posix", "linux", "debian", "windows", "fedora", "ubuntu", "macos",
            "raspbian", "qnx", "cygwin", "freebsd", "solaris", "sunos").collect(Collectors.toSet()));

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
        if (Files.exists(Paths.get("/bin/bash")) || Files.exists(Paths.get("/usr/bin/bash"))) {
            ranks.put("unix", 3);
            ranks.put("posix", 3);
        }
        if (Files.exists(Paths.get("/proc"))) {
            ranks.put("linux", 10);
        }
        if (Files.exists(Paths.get("/usr/bin/apt-get"))) {
            ranks.put("debian", 11);
        }
        if (IS_WINDOWS) {
            ranks.put("windows", 5);
        }
        if (Files.exists(Paths.get("/usr/bin/yum"))) {
            ranks.put("fedora", 11);
        }
        if (!IS_WINDOWS) {
            try {
                Process process = new ProcessBuilder().command("sh", "-c", "uname -a").start();
                String sysver = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8.displayName())
                                       .toLowerCase();
                if (sysver.contains("ubuntu")) {
                    ranks.put("ubuntu", 20);
                }
                if (sysver.contains("darwin")) {
                    ranks.put("macos", 20);
                }
                if (sysver.contains("raspbian")) {
                    ranks.put("raspbian", 22);
                }
                if (sysver.contains("qnx")) {
                    ranks.put("qnx", 22);
                }
                if (sysver.contains("cygwin")) {
                    ranks.put("cygwin", 22);
                }
                if (sysver.contains("freebsd")) {
                    ranks.put("freebsd", 22);
                }
                if (sysver.contains("solaris") || sysver.contains("sunos")) {
                    ranks.put("solaris", 22);
                }
            } catch (IOException e) {
                log.error("Fail to get platform information", e);
            }
        }
        return ranks;
    }

    /**
     * Get the most specific platform string for the current system.
     *
     * @return platform
     */
    public static String getPlatform() {
        return RANKS.get().entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).map(Map.Entry::getKey)
                .orElse("all");
    }
}
