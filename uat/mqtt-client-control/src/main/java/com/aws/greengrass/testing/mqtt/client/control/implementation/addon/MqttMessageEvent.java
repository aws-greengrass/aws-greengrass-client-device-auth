/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.addon;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Properties;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventFilter;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Implements received MQTT message event.
 */
public class MqttMessageEvent extends EventImpl {

    private final ConnectionControl connectionControl;

    @Getter
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

    @SuppressWarnings("PMD.CognitiveComplexity")
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

        matched = isRetainMatched(filter.getRetain());
        if (!matched) {
            return false;
        }

        matched = isUserPropertiesMatched(filter.getUserProperties());
        if (!matched) {
            return false;
        }

        matched = isContentTypeMatched(filter.getContentType());
        if (!matched) {
            return false;
        }

        matched = isPayloadFormatIndicatorMatched(filter.getPayloadFormatIndicator());
        if (!matched) {
            return false;
        }

        matched = isMessageExpiryIntervalMatched(filter.getMessageExpiryInterval());
        if (!matched) {
            return false;
        }

        matched = isResponseTopicMatched(filter.getResponseTopic());
        if (!matched) {
            return false;
        }

        matched = isCorrelationDataMatched(filter.getCorrelationData());
        if (!matched) {
            return false;
        }

        // TODO: check QoS ? it can be differ on transmit and receive sides

        // check content
        return comparePayload(filter.getContent(), filter.getIsMessageFull());
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private boolean compareConnection(ConnectionControl expectedConnectionControl, String agentId, Integer connectionId,
                                        String connectionName) {
        // 1'st priority
        if (expectedConnectionControl != null) {
            // compare references !
            return expectedConnectionControl == connectionControl;
        }

        // 2'nd priority
        if (agentId != null && connectionId != null) {
            return agentId.equals(connectionControl.getAgentControl().getAgentId())
                        && connectionId == connectionControl.getConnectionId();
        }

        // 3'th priority
        // check connection name
        return connectionName == null || connectionName.equals(getConnectionName());
    }

    private boolean comparePayload(byte[] expected, Boolean isMessageFull) {
        if (expected == null) {
            return true;
        }

        ByteString byteStringPayload = message.getPayload();
        if (byteStringPayload == null) {
            return false;
        }

        if (isMessageFull == null || isMessageFull) {
            return Arrays.equals(expected, byteStringPayload.toByteArray());
        } else {
            String expectedBeginningMessage = new String(expected, StandardCharsets.UTF_8);
            String actualMessage = byteStringPayload.toString(StandardCharsets.UTF_8);
            return actualMessage.contains(expectedBeginningMessage);
        }
    }

    private boolean isRetainMatched(Boolean retain) {
        return retain == null || retain == message.getRetain();
    }

    private boolean isUserPropertiesMatched(List<Mqtt5Properties> userProperties) {
        return userProperties == null || userProperties.equals(message.getPropertiesList());
    }

    private boolean isContentTypeMatched(String contentType) {
        return contentType == null || contentType.equals(message.getContentType());
    }

    private boolean isPayloadFormatIndicatorMatched(Boolean payloadFormatIndicator) {
        return payloadFormatIndicator == null || payloadFormatIndicator == message.getPayloadFormatIndicator();
    }

    private boolean isMessageExpiryIntervalMatched(Integer messageExpiryInterval) {
        return messageExpiryInterval == null || messageExpiryInterval == message.getMessageExpiryInterval();
    }

    private boolean isResponseTopicMatched(String responseTopic) {
        return responseTopic == null || responseTopic.equals(message.getResponseTopic());
    }

    private boolean isCorrelationDataMatched(byte[] correlationData) {
        if (correlationData == null) {
            return true;
        }

        ByteString byteCorrelationData = message.getCorrelationData();
        if (byteCorrelationData == null) {
            return false;
        }

        return Arrays.equals(correlationData, byteCorrelationData.toByteArray());
    }

    private static boolean isTopicMatched(@NonNull String topic, @NonNull String topicFilter) {
        return MqttTopic.topicIsSupersetOf(topicFilter, topic);
    }
}
