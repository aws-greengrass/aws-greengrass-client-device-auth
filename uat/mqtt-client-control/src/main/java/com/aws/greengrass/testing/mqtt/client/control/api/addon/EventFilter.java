/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.api.addon;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Properties;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import lombok.Getter;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Filter of events in event storage.
 */
@Getter
public final class EventFilter {
    private final Event.Type type;
    private final Long fromTimestamp;           // requires timestamp >= fromTimestamp
    private final Long toTimestamp;             // means timestamp < toTimestamp
    private final ConnectionControl connectionControl;
    private final String agentId;
    private final Integer connectionId;
    private final String connectionName;
    private final String topic;
    private final String topicFilter;
    private final String contentType;
    private final byte[] content;
    private final Boolean retain;
    private final List<Mqtt5Properties> userProperties;
    private final Boolean payloadFormatIndicator;

    EventFilter(Builder builder) {
        super();
        this.type = builder.type;
        this.fromTimestamp = builder.fromTimestamp;
        this.toTimestamp = builder.toTimestamp;
        this.connectionControl = builder.connectionControl;
        this.agentId = builder.agentId;
        this.connectionId = builder.connectionId;
        this.connectionName = builder.connectionName;
        this.topic = builder.topic;
        this.topicFilter = builder.topicFilter;
        this.contentType = builder.contentType;
        this.content = builder.content;
        this.retain = builder.retain;
        this.userProperties = builder.userProperties;
        this.payloadFormatIndicator = builder.payloadFormatIndicator;
    }

    /**
     * Builder of event filter.
     */
    public static class Builder {
        private Event.Type type;
        private Long fromTimestamp;
        private Long toTimestamp;
        private ConnectionControl connectionControl;
        private String agentId;
        private Integer connectionId;
        private String connectionName;
        private String topic;
        private String topicFilter;
        private String contentType;
        private byte[] content;
        private Boolean retain;
        private List<Mqtt5Properties> userProperties;
        private Boolean payloadFormatIndicator;

        /**
         * Sets type of event.
         * Applicable for any type of event
         *
         * @param type the type of events to select by filter
         */
        public Builder withType(@NonNull Event.Type type) {
            this.type = type;
            return this;
        }

        /**
         * Sets `from timestamp` field of filter.
         * Applicable for any type of event
         *
         * @param timestamp the minimal timestamp to select events by filter
         */
        public Builder withFromTimestamp(long timestamp) {
            this.fromTimestamp = timestamp;
            return this;
        }

        /**
         * Sets `to timestamp` field of filter.
         * Applicable for any type of event
         *
         * @param timestamp the maxinal timestamp to select events by filter
         */
        public Builder withToTimestamp(long timestamp) {
            this.toTimestamp = timestamp;
            return this;
        }

        /**
         * Sets connectionControl field of filter.
         * Applicable for types of event related to connection
         *
         * @param connectionControl the exact connectionControl to select events
         */
        public Builder withConnectionControl(@NonNull ConnectionControl connectionControl) {
            this.connectionControl = connectionControl;
            return this;
        }

        /**
         * Sets agentId field of filter.
         * Applicable for types of event related to agent and/or connection
         * Does not used if connection is set
         *
         * @param agentId the id of agent to select events
         */
        public Builder withAgentId(@NonNull String agentId) {
            this.agentId = agentId;
            return this;
        }

        /**
         * Sets connectionId field of filter.
         * Applicable for types of event related to connection
         * Does not used if connection is set
         * Works only if also agentId is set
         *
         * @param connectionId the id of connection to select events
         */
        public Builder withConnectionId(int connectionId) {
            this.connectionId = connectionId;
            return this;
        }

        /**
         * Sets connectionName field of filter.
         * Applicable for types of event related to connection
         * Does not used if connection is or agentId/connectionId are set
         *
         * @param connectionName the id of connection to select events
         */
        public Builder withConnectionName(@NonNull String connectionName) {
            this.connectionName = connectionName;
            return this;
        }

        /**
         * Sets connectionName field of filter.
         * Applicable only for MQTT message events
         *
         * @param topic the topic of MQTT message
         */
        public Builder withTopic(@NonNull String topic) {
            this.topic = topic;
            return this;
        }

        /**
         * Sets connectionName field of filter.
         * Applicable only for MQTT message events
         * Does not used if topic is set
         *
         * @param topicFilter the topic filter (as for subscribe) of MQTT message
         */
        public Builder withTopicFilter(@NonNull String topicFilter) {
            this.topicFilter = topicFilter;
            return this;
        }

        /**
         * Sets content field of filter.
         * Applicable only for MQTT message events
         * Both withContent() set the same field
         *
         * @param content the string content of MQTT message
         */
        public Builder withContent(@NonNull String content) {
            return withContent(content.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Sets content field of filter.
         * Applicable only for MQTT message events
         * Both withContent() set the same field
         *
         * @param content the byte array content of MQTT message
         */
        public Builder withContent(@NonNull byte[] content) {
            this.content = content;
            return this;
        }

        /**
         * Sets content field of filter.
         * Applicable only for MQTT message events
         *
         * @param contentType the content type of MQTT message
         */
        public Builder withContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        /**
         * Sets retain flag.
         * Applicable only for MQTT message events
         *
         * @param retain the retain flag of the message or null
         */
        public Builder withRetain(Boolean retain) {
            this.retain = retain;
            return this;
        }

        /**
         * Sets user properties.
         * Applicable only for MQTT message events
         *
         * @param userProperties the user properties of the published message
         */
        public Builder withUserProperties(List<Mqtt5Properties> userProperties) {
            this.userProperties = userProperties;
            return this;
        }

        /**
         * Sets payload format indicator flag.
         * Applicable only for MQTT message events
         *
         * @param payloadFormatIndicator the payload format indicator flag of the message or null
         */
        public Builder withPayloadFormatIndicator(Boolean payloadFormatIndicator) {
            this.payloadFormatIndicator = payloadFormatIndicator;
            return this;
        }

        public EventFilter build() {
            return new EventFilter(this);
        }
    }
}
