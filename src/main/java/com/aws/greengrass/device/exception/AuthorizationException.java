/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.exception;

public class AuthorizationException extends Exception {

    static final long serialVersionUID = -1L;

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(Throwable e) {
        super(e);
    }

    public AuthorizationException(String message, Throwable e) {
        super(message, e);
    }
}
