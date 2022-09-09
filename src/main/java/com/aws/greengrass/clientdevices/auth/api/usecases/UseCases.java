/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api.usecases;

import com.aws.greengrass.dependency.Context;

import java.lang.reflect.Proxy;


public final class UseCases {
    private static UseCases instance;
    private final Context context;

    private UseCases(Context context) {
        this.context = context;
    }

    public static void init(Context context) {
       instance = new UseCases(context);
    }

    private static <I, T extends I> I decorate(T t, Class<I> intrface) {
        UseCaseDecorator useCaseDecorator = new UseCaseDecorator(t);
        return (I) Proxy.newProxyInstance(intrface.getClassLoader(), new Class[]{intrface}, useCaseDecorator);
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
     */
    public static UseCases provide(Class clazz, Object ob) {
        checkCanRun();
        instance.context.put(clazz, ob);
        return instance;
    }

    /**
     * Builds a brand-new instance of the use case, injecting the dependencies on the context. It returns a decorated
     * use case that logs the execution time.
     *
     * @param clazz Use Case class to be built
     * @param <C>   Use Case concrete class
     */
    public static <C extends UseCase> UseCase get(Class<C> clazz) {
        checkCanRun();
        return decorate(instance.context.newInstance(clazz), UseCase.class);
    }
}
