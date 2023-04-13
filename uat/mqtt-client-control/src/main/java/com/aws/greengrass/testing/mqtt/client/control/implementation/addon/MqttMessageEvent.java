/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.addon;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventFilter;
import com.google.protobuf.ByteString;
import lombok.NonNull;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Implements received MQTT message event.
 */
public class MqttMessageEvent extends EventImpl {

    private final ConnectionControl connectionControl;
    private final Mqtt5Message message;

    /**
     * Creates instance of MqttMessageEvent.
     *
     * @param connectionControl the connection control which receives that message
     * @param message the gRPC presentation of received MQTT message
     */
    public MqttMessageEvent(@NonNull ConnectionControl connectionControl, @NonNull Mqtt5Message message) {
        super(Type.EVENT_TYPE_MQTT_MESSAGE);
        this.connectionControl = connectionControl;
        this.message = message;
    }

    @Override
    public String getConnectionName() {
        return connectionControl.getConnectionName();
    }

    @Override
    public boolean isMatched(@NonNull EventFilter filter) {
        // check type and timestamp
        boolean matched = super.isMatched(filter);
        if (!matched) {
            return false;
        }

        // check connection
        matched = compareConnection(filter.getConnectionControl(), filter.getAgentId(), filter.getConnectionId(), 
                                    filter.getConnectionName());
        if (!matched) {
            return false;
        }

        // check topic
        final String topic = filter.getTopic();
        if (topic != null) {
            if (!topic.equals(message.getTopic())) {
                return false;
            }
        } else {
            // or topic filter
            final String topicFilter = filter.getTopicFilter();
            if (topicFilter != null && !isTopicMatched(message.getTopic(), topicFilter)) {
                return false;
            }
        }

        // TODO: check QoS ?

        // check content
        return comparePayload(filter.getContent());
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private boolean compareConnection(ConnectionControl expectedConnectionControl, String agentId, Integer connectionId,
                                        String connectionName) {
        // 1'st priority
        if (expectedConnectionControl != null) {
            // compare references !
            return expectedConnectionControl == connectionControl;
        }

        // 2'st priority
        if (agentId != null && connectionId != null) {
            return agentId.equals(connectionControl.getAgentControl().getAgentId())
                        && connectionId == connectionControl.getConnectionId();
        }

        // 3'th priority
        // check connection name
        return connectionName == null || connectionName.equals(getConnectionName());
    }

    private boolean comparePayload(byte[] expected) {
        if (expected == null) {
            return true;
        }

        ByteString byteStringPayload = message.getPayload();
        if (byteStringPayload == null) {
            return false;
        } else {
            return Arrays.equals(expected, byteStringPayload.toByteArray());
        }
    }

    private static boolean isTopicMatched(@NonNull String topic, @NonNull String topicFilter) {
        final String regex = topicFilter.replace("+", "[^/]+").replace("#", ".+");
        return Pattern.matches(regex, topic);
    }
}