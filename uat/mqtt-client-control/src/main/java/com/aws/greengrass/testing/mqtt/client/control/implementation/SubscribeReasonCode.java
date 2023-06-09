/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation;

/**
 * Reason code inside DisconnectPackets.  Helps determine why a connection was terminated.
 *
 * <p>Enum values match <a href="https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901208">MQTT5 spec</a> encoding values.
 */
public enum SubscribeReasonCode {
    /**
     * Returned when the subscription is accepted and
     * the maximum QoS sent will be QoS 0.
     * This might be a lower QoS than was requested.
     */
    GRANTED_QOS_0(0),

    /**
     * Returned when the subscription is accepted and
     * the maximum QoS sent will be QoS 1.
     * This might be a lower QoS than was requested.
     */
    GRANTED_QOS_1(1),

    /**
     * Returned when the subscription is accepted and
     * the maximum QoS sent will be QoS 2.
     * This might be a lower QoS than was requested.
     */
    GRANTED_QOS_2(2),

    /**
     * Returned when the subscription is not accepted and the Server either does not wish to reveal the reason
     * or none of the other Reason Codes apply.
     *
     * <p>May be sent by the client or the server.
     */
    UNSPECIFIED_ERROR(128),

    /**
     * Returned when the client is not authorized to make this subscription.
     *
     * <p>May only be sent by the server.
     */
    NOT_AUTHORIZED(135),

    /**
     * Returned when the topic filter name is correctly formed but not accepted by the server.
     *
     * <p>May only be sent by the server.
     */
    TOPIC_FILTER_INVALID(143),

    /**
     * Returned when specified Packet Identifier is already in use.
     */
    PACKET_IDENTIFIER_IN_USE(145),

    /**
     * Returned when an implementation or administrative imposed limit has been exceeded.
     *
     * <p>May be sent by the client or the server.
     */
    QUOTA_EXCEEDED(151);

    private int reasonCode;

    SubscribeReasonCode(int code) {
        reasonCode = code;
    }

    /**
     * Gets integer value of enum.
     *
     * @return The native enum integer value associated with this Java enum value
     */
    public int getValue() {
        return reasonCode;
    }
}

