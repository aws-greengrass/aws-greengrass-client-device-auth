/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.dependency.Context;
import com.aws.greengrass.util.CrashableFunction;


public class UseCases {
    private final Context context;
    private static UseCases instance;

    // Delegates to CrashableFunction but provides a domain rich alias
    public interface UseCase<R, D, E extends Exception> extends CrashableFunction<D, R, E> {}

    private UseCases(Context context) {
        this.context = context;
    }

    public static void init(Context context) {
       instance = new UseCases(context);
    }

    private static void checkCanRun() {
        if (instance == null) {
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
    public static <T> UseCases provide(Class<T> clazz, T ob) {
        checkCanRun();
        instance.context.put(clazz, ob);
        return instance;
    }

    /**
     * Wrapper around context that will generate a new instance of a Use Case with
     * the latest dependencies on the context.
     *
     * @param clazz Use Case class to be built
     * @param <C>   Use Case concrete class
     */
    public static <C extends UseCase> C get(Class<C> clazz) {
        checkCanRun();
        return instance.context.newInstance(clazz);
    }
}
