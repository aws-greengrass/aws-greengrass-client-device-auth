/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

public class CsrProcessingException extends Exception {
    static final long serialVersionUID = -3387516993124229948L;
    private static final String ERROR_MSG_FORMAT = "Unable to process CSR: %s";

    public CsrProcessingException(String csr) {
        super(String.format(ERROR_MSG_FORMAT, csr));
    }

    public CsrProcessingException(String csr, Throwable cause) {
        super(String.format(ERROR_MSG_FORMAT, csr), cause);
    }
}
