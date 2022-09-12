/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;
import com.aws.greengrass.util.CrashableFunction;


public class UseCases {
    private Context context;
    public static final Logger logger = LogManager.getLogger(UseCases.class);

    // Delegates to CrashableFunction but provides a domain rich alias
    public interface UseCase<R, D, E extends Exception> extends CrashableFunction<D, R, E> {}

    public void init(Context context) {
        this.context = context;
    }



    private void checkCanRun() {
        if (context == null) {
            throw new RuntimeException("No UseCases instance found, make sure you initialize them first");
        }
    }

    /**
     * Allows to provide a specific dependency we want to be injected on the use case being tested.
     *
     * @param clazz Class of the instance you want to inject1
     * @param ob Concrete instance of the class
     * @param <T> instance type
     */
    public <T> UseCases provide(Class<T> clazz, T ob) {
        checkCanRun();
        context.put(clazz, ob);
        return this;
    }

    /**
     * Wrapper around context that will generate a new instance of a Use Case with
     * the latest dependencies on the context.
     *
     * @param clazz Use Case class to be built
     * @param <C>   Use Case concrete class
     */
    public <C extends UseCase> C get(Class<C> clazz) {
        checkCanRun();
        return context.newInstance(clazz);
    }
}
