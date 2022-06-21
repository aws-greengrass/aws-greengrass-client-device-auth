/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.exception;

public class CloudServiceInteractionException extends RuntimeException {

    private static final long serialVersionUID = -1L;

    public CloudServiceInteractionException(String message) {
        super(message);
    }

    public CloudServiceInteractionException(Throwable e) {
        super(e);
    }

    public CloudServiceInteractionException(String message, Throwable e) {
        super(message, e);
    }
}
