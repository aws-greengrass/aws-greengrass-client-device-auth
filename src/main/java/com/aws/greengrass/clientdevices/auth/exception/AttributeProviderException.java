/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.exception;

public class AttributeProviderException extends Exception {
    private static final long serialVersionUID = -1L;

    public AttributeProviderException(String message) {
        super(message);
    }

    public AttributeProviderException(Throwable e) {
        super(e);
    }

    public AttributeProviderException(String message, Throwable e) {
        super(message, e);
    }
}
