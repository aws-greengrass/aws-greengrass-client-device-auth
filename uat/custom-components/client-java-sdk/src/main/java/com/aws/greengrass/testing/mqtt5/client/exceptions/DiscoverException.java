/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.exceptions;

/**
 * Client's exception related to discover parts.
 */
public class DiscoverException extends ClientException {
    private static final long serialVersionUID = -2081564070408021325L;

    public DiscoverException() {
        super();
    }

    public DiscoverException(String message) {
        super(message);
    }

    public DiscoverException(String message, Throwable cause) {
        super(message, cause);
    }

    public DiscoverException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public DiscoverException(Throwable cause) {
        super(cause);
    }
}
