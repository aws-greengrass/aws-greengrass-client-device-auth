/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.exception;

public class InvalidConfigurationException extends Exception {
    private static final long serialVersionUID = -1L;

    public InvalidConfigurationException(String message) {
        super(message);
    }

    public InvalidConfigurationException(Throwable e) {
        super(e);
    }

    public InvalidConfigurationException(String message, Throwable e) {
        super(message, e);
    }
}
