/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.infra;

import com.aws.greengrass.clientdevices.auth.configuration.InfrastructureConfiguration;
import com.aws.greengrass.clientdevices.auth.util.ResizableLinkedBlockingQueue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

public final class CDAExecutor implements Executor, Consumer<InfrastructureConfiguration> {
    private final ThreadPoolExecutor executor;
    private final BlockingQueue<Runnable> executorQueue;

    /**
     * Creates a new CDAExecutor with the given underlying ThreadPoolExecutor.
     * The underlying work queue should be resizeable in order to respond
     * to component configuration updates.
     *
     * @param executor Thread pool executor to be used as the underlying work thread pool.
     */
    public CDAExecutor(ThreadPoolExecutor executor) {
        this.executor = executor;
        this.executorQueue = executor.getQueue();
    }

    public void shutdown() {
        executor.shutdown();
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    public <T> Future<T> execute(Callable<T> callable) {
        return executor.submit(callable);
    }

    @Override
    public void accept(InfrastructureConfiguration infrastructureConfiguration) {
        // Thread pool size may not shrink below core pool size, so reduce this accordingly
        int maxPoolSize = infrastructureConfiguration.getThreadPoolSize();

        if (maxPoolSize > executor.getCorePoolSize()) {
            maxPoolSize = executor.getCorePoolSize();
        }
        executor.setMaximumPoolSize(maxPoolSize);

        // Only attempt to resize the underlying work queue if it is a ResizeableLinkedBlockingQueue
        if (executorQueue instanceof ResizableLinkedBlockingQueue) {
            int queueDepth = infrastructureConfiguration.getWorkQueueDepth();
            ((ResizableLinkedBlockingQueue) executorQueue).resize(queueDepth);
        }
    }
}
