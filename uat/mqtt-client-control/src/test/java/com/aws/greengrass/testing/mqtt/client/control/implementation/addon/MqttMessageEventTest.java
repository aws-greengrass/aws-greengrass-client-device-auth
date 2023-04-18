/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.addon;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.Event;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventFilter;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttMessageEventTest {
    static final String CONNECTION_NAME = "connection_name";
    static final String TOPIC = "topic/name";
    static final String SINGLE_LEVEL_FILTER = "topic/+";
    static final String CONTENT = "message_content";

    @Mock
    private ConnectionControl connectionControl;

    @Mock
    private Mqtt5Message message;

    MqttMessageEvent mqttMessageEvent;

    @BeforeEach
    void setup() {
        mqttMessageEvent = new MqttMessageEvent(connectionControl, message);
    }

    @Test
    void GIVEN_mqtt_message_event_WHEN_get_type_THEN_mqtt_message() {
        // WHEN
        final Event.Type actualType = mqttMessageEvent.getType();

        // THEN
        assertEquals(Event.Type.EVENT_TYPE_MQTT_MESSAGE, actualType);
    }

    @Test
    void GIVEN_mqtt_message_event_WHEN_get_timestamp_THEN_event_added_last_seconds() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis();

        // WHEN
        final long actialTimestamp = mqttMessageEvent.getTimestamp();

        // THEN
        // in actialTimestamp we expecting time when setup() has been called it should be less or equals to testRunTimestamp
        assertTrue(actialTimestamp <= testRunTimestamp);
        //  but not more than 1 seconds (can failed on slow machines)
        assertTrue(actialTimestamp + 1000 >= testRunTimestamp);
    }

    @Test
    void GIVEN_connection_with_name_WHEN_get_connection_name_THEN_expected_name_is_returned() {
        // GIVEN
        final String expectedConnectionName = CONNECTION_NAME;
        when(connectionControl.getConnectionName()).thenReturn(expectedConnectionName);

        // WHEN
        final String actualConnectionName = mqttMessageEvent.getConnectionName();

        // THEN
        assertEquals(expectedConnectionName, actualConnectionName);
        verify(connectionControl).getConnectionName();
    }

    @Test
    void GIVEN_filter_matched_message_WHEN_is_matched_THEN_yes() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis();
        final String topic = TOPIC;
        final String content = CONTENT;

        lenient().when(message.getTopic()).thenReturn(topic);
        lenient().when(message.getPayload()).thenReturn(ByteString.copyFromUtf8(content));

        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withToTimestamp(testRunTimestamp)
                                        .withConnectionControl(connectionControl)
                                        .withTopic(topic)
                                        .withContent(content)
                                        .build();


        // WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        // THEN
        assertTrue(result);
        verify(message).getTopic();
        verify(message).getPayload();
    }

    // TODO: test by type when more types will been added

    @Test
    void GIVEN_to_timestamp_is_not_matched_WHEN_is_matched_THEN_not() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis() - 100;
        final String topic = TOPIC;
        final String content = CONTENT;

        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withToTimestamp(testRunTimestamp)
                                        .withConnectionControl(connectionControl)
                                        .withTopic(topic)
                                        .withContent(content)
                                        .build();


        // WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        // THEN
        assertFalse(result);
    }

    @Test
    void GIVEN_from_timestamp_is_not_matched_WHEN_is_matched_THEN_not() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis() + 100;
        final String topic = TOPIC;
        final String content = CONTENT;

        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withFromTimestamp(testRunTimestamp)
                                        .withConnectionControl(connectionControl)
                                        .withTopic(topic)
                                        .withContent(content)
                                        .build();


        // WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        // THEN
        assertFalse(result);
    }

    @Test
    void GIVEN_topic_is_not_matched_WHEN_is_matched_THEN_not() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis();
        final String topic = TOPIC;
        final String content = CONTENT;

        lenient().when(message.getTopic()).thenReturn(topic + "ADDITION");

        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withToTimestamp(testRunTimestamp)
                                        .withConnectionControl(connectionControl)
                                        .withTopic(topic)
                                        .withContent(content)
                                        .build();


        // WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        // THEN
        assertFalse(result);
    }

    @Test
    void GIVEN_content_is_not_matched_WHEN_is_matched_THEN_not() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis();
        final String topic = TOPIC;
        final String content = CONTENT;

        lenient().when(message.getTopic()).thenReturn(topic);
        lenient().when(message.getPayload()).thenReturn(ByteString.copyFromUtf8(content + "ADDITION"));

        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withToTimestamp(testRunTimestamp)
                                        .withConnectionControl(connectionControl)
                                        .withTopic(topic)
                                        .withContent(content)
                                        .build();


        // WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        // THEN
        assertFalse(result);
    }

    @Test
    void GIVEN_topic_and_matched_filter_matched_WHEN_is_matched_THEN_is() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis();
        final String topic = TOPIC;
        final String content = CONTENT;
        final String filter = SINGLE_LEVEL_FILTER;

        lenient().when(message.getTopic()).thenReturn(topic);
        lenient().when(message.getPayload()).thenReturn(ByteString.copyFromUtf8(content));

        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withToTimestamp(testRunTimestamp)
                                        .withConnectionControl(connectionControl)
                                        .withTopicFilter(filter)
                                        .withContent(content)
                                        .build();


        // WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        // THEN
        assertTrue(result);
        verify(message).getTopic();
        verify(message).getPayload();
    }
}
