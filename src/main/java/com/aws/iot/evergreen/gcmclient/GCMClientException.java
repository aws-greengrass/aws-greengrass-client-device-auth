/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.gcmclient;

public class GCMClientException extends Exception {
    static final long serialVersionUID = -3387516993124229948L;

    public GCMClientException(String message, Throwable e) {
        super(message, e);
    }

    public GCMClientException(String message) {
        super(message);
    }
}
