/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.util.CrashableFunction;


public class UseCases {
    private final Context context;

    // Delegates to CrashableFunction but provides a domain rich alias
    public interface UseCase<R, D, E extends Exception> extends CrashableFunction<D, R, E> {}

    public UseCases(Topics topics) {
        this.context = topics.getContext();
    }

    public <C extends UseCase> C get(Class<C> clazz) {
        return context.get(clazz);
    }
}
