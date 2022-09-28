/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.configuration;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.util.Coerce;
import lombok.Value;

/**
 * Represents client device infrastructure configuration.
 * </p>
 * NOTE: currently we're shoving some unrelated things under the `performance` key.
 * Things like maxActiveAuthTokens and refresh periods should be grouped separately.
 * <p>
 * |---- configuration
 * |    |---- performance:
 * |          |---- cloudRequestQueueSize: "..."
 * |          |---- maxConcurrentCloudRequests: [...]
 * </p>
 */
@Value
public final class InfrastructureConfiguration {
    public static final int DEFAULT_WORK_QUEUE_DEPTH = 100;
    public static final int DEFAULT_THREAD_POOL_SIZE = 1;

    public static final String PERFORMANCE_TOPIC = "performance";
    // TODO: Need to determine if this is useful. We may want different numbers for internal
    //  vs external usage - e.g. IPC work queue throttling vs internal cert refreshes
    public static final String WORK_QUEUE_DEPTH = "cloudRequestQueueSize"; // Deprecate?
    public static final String THREAD_POOL_SIZE = "maxConcurrentCloudRequests"; // Deprecate?

    int workQueueDepth;
    int threadPoolSize;

    private InfrastructureConfiguration(int workQueueDepth, int threadPoolSize) {
        this.workQueueDepth = workQueueDepth;
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Factory method for creating an immutable InfrastructureConfiguration from the service configuration.
     *
     * @param configurationTopics the configuration key of the service configuration
     */
    public static InfrastructureConfiguration from(Topics configurationTopics) {
        Topics infraTopics = configurationTopics.lookupTopics(PERFORMANCE_TOPIC);

        return new InfrastructureConfiguration(
                getWorkQueueDepthFromConfiguration(infraTopics),
                getThreadPoolSizeFromConfiguration(infraTopics)
        );
    }

    private static int getWorkQueueDepthFromConfiguration(Topics infraTopics) {
        return Coerce.toInt(infraTopics.findOrDefault(DEFAULT_WORK_QUEUE_DEPTH, WORK_QUEUE_DEPTH));
    }

    private static int getThreadPoolSizeFromConfiguration(Topics infraTopics) {
        return Coerce.toInt(infraTopics.findOrDefault(DEFAULT_THREAD_POOL_SIZE, THREAD_POOL_SIZE));
    }
}
