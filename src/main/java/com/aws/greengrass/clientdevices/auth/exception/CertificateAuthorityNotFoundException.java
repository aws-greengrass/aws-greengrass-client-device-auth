/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.exception;

public class CertificateAuthorityNotFoundException extends Exception {

    private static final long serialVersionUID = -1L;

    public CertificateAuthorityNotFoundException(String message) {
        super(message);
    }

    public CertificateAuthorityNotFoundException(Throwable e) {
        super(e);
    }

    public CertificateAuthorityNotFoundException(String message, Throwable e) {
        super(message, e);
    }
}
