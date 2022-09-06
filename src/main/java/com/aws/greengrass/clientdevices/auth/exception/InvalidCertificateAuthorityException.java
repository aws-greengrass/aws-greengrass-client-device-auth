/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.exception;

public class InvalidCertificateAuthorityException extends RuntimeException {
    private static final long serialVersionUID = -1L;

    public InvalidCertificateAuthorityException(String message) {
        super(message);
    }

    public InvalidCertificateAuthorityException(Throwable e) {
        super(e);
    }

    public InvalidCertificateAuthorityException(String message, Throwable e) {
        super(message, e);
    }
}
