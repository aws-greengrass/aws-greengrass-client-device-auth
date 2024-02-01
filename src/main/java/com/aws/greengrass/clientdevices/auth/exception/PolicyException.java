/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.exception;

public class PolicyException extends Exception {
    private static final long serialVersionUID = -1L;

    public PolicyException(String message) {
        super(message);
    }

    public PolicyException(Throwable e) {
        super(e);
    }

    public PolicyException(String message, Throwable e) {
        super(message, e);
    }
}
