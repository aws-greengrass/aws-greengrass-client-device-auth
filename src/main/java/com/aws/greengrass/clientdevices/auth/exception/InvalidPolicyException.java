/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.exception;

public class InvalidPolicyException extends Exception {

    private static final long serialVersionUID = -1L;

    public InvalidPolicyException(String message) {
        super(message);
    }

    public InvalidPolicyException(Throwable e) {
        super(e);
    }

    public InvalidPolicyException(String message, Throwable e) {
        super(message, e);
    }
}
