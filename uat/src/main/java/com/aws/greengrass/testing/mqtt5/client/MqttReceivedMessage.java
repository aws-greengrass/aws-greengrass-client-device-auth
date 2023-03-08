/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client;

import lombok.Builder;
import lombok.Data;

/**
 * Contains information about received MQTT v5.0 message.
 */
@Data
@Builder
public class MqttReceivedMessage {
    /** QoS value. */
    int qos;

    /** Retain flag. */
    boolean retain;

    /** Topic of message. */
    String topic;

    /** Payload of message. */
    byte[] payload;

    // TODO: add user's properties and so one
}
