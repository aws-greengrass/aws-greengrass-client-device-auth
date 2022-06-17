/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.device.exception;

public class CertificateGenerationException extends Exception {

    private static final long serialVersionUID = -1L;

    public CertificateGenerationException(String message) {
        super(message);
    }

    public CertificateGenerationException(Throwable e) {
        super(e);
    }

    public CertificateGenerationException(String message, Throwable e) {
        super(message, e);
    }
}
