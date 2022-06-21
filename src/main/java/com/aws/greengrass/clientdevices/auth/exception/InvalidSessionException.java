/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.exception;

public class InvalidSessionException extends AuthorizationException {
    private static final long serialVersionUID = -1L;

    public InvalidSessionException(String message) {
        super(message);
    }

    public InvalidSessionException(Throwable e) {
        super(e);
    }

    public InvalidSessionException(String message, Throwable e) {
        super(message, e);
    }
}
