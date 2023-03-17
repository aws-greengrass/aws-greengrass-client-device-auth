/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.exceptions;

/**
 * Client's exception related to gRPC parts.
 */
public class GRPCException extends ClientException {
    private static final long serialVersionUID = -5568807468213198312L;

    public GRPCException() {
        super();
    }

    public GRPCException(String message) {
        super(message);
    }

    public GRPCException(String message, Throwable cause) {
        super(message, cause);
    }

    public GRPCException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public GRPCException(Throwable cause) {
        super(cause);
    }
}
