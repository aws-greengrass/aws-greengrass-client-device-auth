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
    int qos;
    boolean retain;
    String topic;
    byte[] payload;
}
