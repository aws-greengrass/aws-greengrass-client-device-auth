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
public enum PublishReasonCode {
    /**
     * Returned when the message is accepted.
     * Publication of the QoS 1 message proceeds.
     */
    SUCCESS(0),

    /**
     * Returned when the message is accepted but there are no subscribers.
     * This is sent only by the Server.
     * If the Server knows that there are no matching subscribers,
     * it MAY use this Reason Code instead of 0x00 (Success).
     */
    NONE(16),

    /**
     * Returned when the  receiver does not accept the publish,
     * but either does not want to reveal the reason,
     * or it does not match one of the other values.
     */
    UNSPECIFIED_ERROR(128),

    /**
     * Returned when the PUBLISH is valid but the receiver is not willing to accept it.
     */
    IMPLEMENTATION_SPECIFIC_ERROR(131),

    /**
     * Returned when the PUBLISH is not authorized.
     */
    NOT_AUTHORIZED(135),

    /**
     * Returned when the topic name is not malformed,
     * but is not accepted by the client or server.
     */
    TOPIC_NAME_INVALID(144),

    /**
     * Returned when specified Packet Identifier is already in use.
     * This might indicate a mismatch in the session state between the client and server.
     */
    PACKET_IDENTIFIER_IN_USE(145),

    /**
     * Returned when an implementation or administrative imposed limit has been exceeded.
     */
    QUOTA_EXCEEDED(151),

    /**
     * Returned when the payload format does not match the specified Payload Format Indicator.
     */
    PAYLOAD_FORMAT_INVALID(153);

    private int reasonCode;

    PublishReasonCode(int code) {
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

