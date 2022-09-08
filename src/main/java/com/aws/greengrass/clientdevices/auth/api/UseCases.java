/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.api;

import com.aws.greengrass.config.Topics;
import com.aws.greengrass.dependency.Context;

import javax.inject.Inject;


public class UseCases {
    private final Context context;

    public interface UseCase<R, D> {
        @SuppressWarnings("PMD.SignatureDeclareThrowsException")
        R execute(D dto) throws Exception;
    }

    @Inject
    public UseCases(Topics topics) {
        this.context = topics.getContext();
    }

    public <C extends UseCase> C get(Class<C> clazz) {
        return context.get(clazz);
    }
}
