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
public enum DisconnectReasonCode {
    /**
     * Returned when the remote endpoint wishes to disconnect normally.
     * Will not trigger the publish of a Will message if a
     * Will message was configured on the connection.
     *
     * <p>May be sent by the client or server.
     */
    NORMAL_DISCONNECTION(0),

    /**
     * Returns when the client wants to disconnect but requires that the server publish the Will message configured
     * on the connection.
     *
     * <p>May only be sent by the client.
     */
    DISCONNECT_WITH_WILL_MESSAGE(4),

    /**
     * Returned when the connection was closed but the sender does not want to specify a reason or none
     * of the other reason codes apply.
     *
     * <p>May be sent by the client or the server.
     */
    UNSPECIFIED_ERROR(128),

    /**
     * Indicates the remote endpoint received a packet that does not conform to the MQTT specification.
     *
     * <p>May be sent by the client or the server.
     */
    MALFORMED_PACKET(129),

    /**
     * Returned when an unexpected or out-of-order packet was received by the remote endpoint.
     *
     * <p>May be sent by the client or the server.
     */
    PROTOCOL_ERROR(130),

    /**
     * Returned when a valid packet was received by the remote endpoint,
     * but could not be processed by the current implementation.
     *
     * <p>May be sent by the client or the server.
     */
    IMPLEMENTATION_SPECIFIC_ERROR(131),

    /**
     * Returned when the remote endpoint received a packet that represented an operation that was not authorized within
     * the current connection.
     *
     * <p>May only be sent by the server.
     */
    NOT_AUTHORIZED(135),

    /**
     * Returned when the server is busy and cannot continue processing packets from the client.
     *
     * <p>May only be sent by the server.
     */
    SERVER_BUSY(137),

    /**
     * Returned when the server is shutting down.
     *
     * <p>May only be sent by the server.
     */
    SERVER_SHUTTING_DOWN(139),

    /**
     * Returned when the server closes the connection because no packet from the client has been received in
     * 1.5 times the KeepAlive time set when the connection was established.
     *
     * <p>May only be sent by the server.
     */
    KEEP_ALIVE_TIMEOUT(141),

    /**
     * Returned when the server has established another connection with the same client ID as a client's current
     * connection, causing the current client to become disconnected.
     *
     * <p>May only be sent by the server.
     */
    SESSION_TAKEN_OVER(142),

    /**
     * Returned when the topic filter name is correctly formed but not accepted by the server.
     *
     * <p>May only be sent by the server.
     */
    TOPIC_FILTER_INVALID(143),

    /**
     * Returned when topic name is correctly formed, but is not accepted.
     *
     * <p>May be sent by the client or the server.
     */
    TOPIC_NAME_INVALID(144),

    /**
     * Returned when the remote endpoint reached a state where there were more in-progress QoS1+ publishes then the
     * limit it established for itself when the connection was opened.
     *
     * <p>May be sent by the client or the server.
     */
    RECEIVE_MAXIMUM_EXCEEDED(147),

    /**
     * Returned when the remote endpoint receives a PublishPacket that contained a topic alias greater than the
     * maximum topic alias limit that it established for itself when the connection was opened.
     *
     * <p>May be sent by the client or the server.
     */
    TOPIC_ALIAS_INVALID(148),

    /**
     * Returned when the remote endpoint received a packet whose size was greater than the maximum packet size limit
     * it established for itself when the connection was opened.
     *
     * <p>May be sent by the client or the server.
     */
    PACKET_TOO_LARGE(149),

    /**
     * Returned when the remote endpoint's incoming data rate was too high.
     *
     * <p>May be sent by the client or the server.
     */
    MESSAGE_RATE_TOO_HIGH(150),

    /**
     * Returned when an internal quota of the remote endpoint was exceeded.
     *
     * <p>May be sent by the client or the server.
     */
    QUOTA_EXCEEDED(151),

    /**
     * Returned when the connection was closed due to an administrative action.
     *
     * <p>May be sent by the client or the server.
     */
    ADMINISTRATIVE_ACTION(152),

    /**
     * Returned when the remote endpoint received a packet where payload format did not match the format specified
     * by the payload format indicator.
     *
     * <p>May be sent by the client or the server.
     */
    PAYLOAD_FORMAT_INVALID(153),

    /**
     * Returned when the server does not support retained messages.
     *
     * <p>May only be sent by the server.
     */
    RETAIN_NOT_SUPPORTED(154),

    /**
     * Returned when the client sends a QoS that is greater than the maximum QOS established when the connection was
     * opened.
     *
     * <p>May only be sent by the server.
     */
    QOS_NOT_SUPPORTED(155),

    /**
     * Returned by the server to tell the client to temporarily use a different server.
     *
     * <p>May only be sent by the server.
     */
    USE_ANOTHER_SERVER(156),

    /**
     * Returned by the server to tell the client to permanently use a different server.
     *
     * <p>May only be sent by the server.
     */
    SERVER_MOVED(157),

    /**
     * Returned by the server to tell the client that shared subscriptions are not supported on the server.
     *
     * <p>May only be sent by the server.
     */
    SHARED_SUBSCRIPTIONS_NOT_SUPPORTED(158),

    /**
     * Returned when the server disconnects the client due to the connection rate being too high.
     *
     * <p>May only be sent by the server.
     */
    CONNECTION_RATE_EXCEEDED(159),

    /**
     * Returned by the server when the maximum connection time authorized for the connection was exceeded.
     *
     * <p>May only be sent by the server.
     */
    MAXIMUM_CONNECT_TIME(160),

    /**
     * Returned by the server when it received a SubscribePacket with a subscription identifier, but the server does
     * not support subscription identifiers.
     *
     * <p>May only be sent by the server.
     */
    SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED(161),

    /**
     * Returned by the server when it received a SubscribePacket with a wildcard topic filter, but the server does
     * not support wildcard topic filters.
     *
     * <p>May only be sent by the server.
     */
    WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED(162);

    private int reasonCode;

    DisconnectReasonCode(int code) {
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

