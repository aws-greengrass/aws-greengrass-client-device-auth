/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api.usecases;

import com.aws.greengrass.logging.api.Logger;
import com.aws.greengrass.logging.impl.LogManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class UseCaseDecorator implements InvocationHandler {
    private final Object useCase;
    private static final Logger logger = LogManager.getLogger(UseCaseDecorator.class);


    public UseCaseDecorator(Object useCase) {
        this.useCase = useCase;
    }

    @Override
    public Object  invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            long start = System.currentTimeMillis();

            Object result = method.invoke(useCase, args);

            long elapsed = System.currentTimeMillis() - start;
            logger.info("Ran use case: {} in {}", useCase.getClass().getName(), elapsed);
            return result;

        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }
}
