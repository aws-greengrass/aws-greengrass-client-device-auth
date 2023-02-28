/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.exceptions;

/**
 * Client's exception related to MQTT parts.
 */
public class MqttException extends ClientException {
    private static final long serialVersionUID = -2081564070408021327L;

    public MqttException() {
        super();
    }

    public MqttException(String message) {
        super(message);
    }

    public MqttException(String message, Throwable cause) {
        super(message, cause);
    }

    public MqttException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public MqttException(Throwable cause) {
        super(cause);
    }
}
