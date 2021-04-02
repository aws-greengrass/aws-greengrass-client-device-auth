/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.cisclient;

public class CISClientException extends Exception {
    static final long serialVersionUID = -3387516993124229948L;

    public CISClientException(String message, Throwable e) {
        super(message, e);
    }

    public CISClientException(String message) {
        super(message);
    }
}
