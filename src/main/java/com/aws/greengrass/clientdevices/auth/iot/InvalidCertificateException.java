/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot;

public class InvalidCertificateException extends Exception {

    private static final long serialVersionUID = -1L;

    public InvalidCertificateException(String message) {
        super(message);
    }

    public InvalidCertificateException(Throwable e) {
        super(e);
    }

    public InvalidCertificateException(String message, Throwable e) {
        super(message, e);
    }
}
