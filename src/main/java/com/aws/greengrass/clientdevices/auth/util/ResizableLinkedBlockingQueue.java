/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.util;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Wrapper for LinkedBlockingQueue which lets us resize on demand.
 * When grown, the queue accepts new entries immediately.
 * When shrunk, all members of the queue remain, but new entries will be rejected until the queue size decreases
 * under the capacity.
 */
public class ResizableLinkedBlockingQueue<T> extends LinkedBlockingQueue<T> {
    private static final long serialVersionUID = -6903933977591709194L;

    private volatile int capacity;

    public ResizableLinkedBlockingQueue(int capacity) {
        super();
        this.capacity = capacity;
    }

    @Override
    public boolean offer(T t) {
        // If the current queue is at or over capacity, then reject new requests.
        // This means that if we resize to be smaller, we will process all the committed work, but won't accept
        // new work until the queue size is back under the limit.
        if (size() >= capacity) {
            return false;
        }
        return super.offer(t);
    }

    public void resize(int newCapacity) {
        capacity = newCapacity;
    }

    @Override
    public int remainingCapacity() {
        return capacity - size(); // might be negative!
    }

    public int capacity() {
        return capacity;
    }
}
