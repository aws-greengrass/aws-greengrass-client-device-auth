/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.dependency.Context;


public class UseCases {
    private Context context;

    public interface UseCase<R, D> {
        @SuppressWarnings("PMD.SignatureDeclareThrowsException")
        R apply(D var1) throws Exception;
    }

    public UseCases() {
    }

    public UseCases(Context context) {
        this.context = context;
    }

    public void init(Context context) {
        this.context = context;
    }

    private void checkCanRun() {
        if (context == null) {
            throw new RuntimeException("No UseCases instance found, make sure you initialize them first");
        }
    }

    /**
     * Wrapper around context.
     *
     * @param clazz Use Case class to be built
     * @param <C>   Use Case concrete class
     */
    public <C extends UseCase> C get(Class<C> clazz) {
        checkCanRun();
        return context.get(clazz);
    }
}
