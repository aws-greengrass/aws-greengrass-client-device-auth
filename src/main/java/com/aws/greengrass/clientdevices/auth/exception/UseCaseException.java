/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.exception;

public class UseCaseException extends Exception {
    private static final long serialVersionUID = -1L;

    public UseCaseException(String message) {
        super(message);
    }

    public UseCaseException(Throwable e) {
        super(e);
    }

    public UseCaseException(String message, Throwable e) {
        super(message, e);
    }
}