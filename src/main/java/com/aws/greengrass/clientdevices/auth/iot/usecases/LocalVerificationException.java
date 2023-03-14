/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.iot.usecases;

public class LocalVerificationException extends Exception {
    private static final long serialVersionUID = 6118835393552886948L;

    public LocalVerificationException(String message) {
        super(message);
    }
}
