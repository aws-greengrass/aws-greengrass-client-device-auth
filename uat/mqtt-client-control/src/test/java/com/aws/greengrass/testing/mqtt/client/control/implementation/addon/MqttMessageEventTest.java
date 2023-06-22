/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.addon;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Properties;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.Event;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventFilter;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

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

    @Test
    void GIVEN_retain_and_matched_filter_WHEN_is_matched_THEN_is() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis();
        final boolean retain = true;

        lenient().when(message.getRetain()).thenReturn(retain);

        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withToTimestamp(testRunTimestamp)
                                        .withConnectionControl(connectionControl)
                                        .withRetain(retain)
                                        .build();


        // WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        // THEN
        assertTrue(result);
        verify(message).getRetain();
    }

    @Test
    void GIVEN_not_retain_and_matched_filter_WHEN_is_matched_THEN_is() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis();
        final boolean retain = false;

        lenient().when(message.getRetain()).thenReturn(retain);

        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withToTimestamp(testRunTimestamp)
                                        .withConnectionControl(connectionControl)
                                        .withRetain(retain)
                                        .build();


        // WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        // THEN
        assertTrue(result);
        verify(message).getRetain();
    }

    @Test
    void GIVEN_retain_and_not_matched_filter_WHEN_is_matched_THEN_is_not() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis();
        final boolean retain = true;

        lenient().when(message.getRetain()).thenReturn(retain);

        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withToTimestamp(testRunTimestamp)
                                        .withConnectionControl(connectionControl)
                                        .withRetain(!retain)
                                        .build();


        // WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        // THEN
        assertFalse(result);
        verify(message).getRetain();
    }

    @Test
    void GIVEN_not_retain_and_not_matched_filter_WHEN_is_matched_THEN_is_not() {
        // GIVEN
        final long testRunTimestamp = System.currentTimeMillis();
        final boolean retain = false;

        lenient().when(message.getRetain()).thenReturn(retain);

        EventFilter eventFilter = new EventFilter.Builder()
                                        .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                                        .withToTimestamp(testRunTimestamp)
                                        .withConnectionControl(connectionControl)
                                        .withRetain(!retain)
                                        .build();


        // WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        // THEN
        assertFalse(result);
        verify(message).getRetain();
    }

    @Test
    void GIVEN_user_properties_and_matched_filter_WHEN_is_matched_THEN_is() {
        final long testRunTimestamp = System.currentTimeMillis();
        final List<Mqtt5Properties> userProperties = Lists.newArrayList(
                Mqtt5Properties.newBuilder().setKey("type").setValue("json").build(),
                Mqtt5Properties.newBuilder().setKey("lang").setValue("ua").build()
        );
        lenient().when(message.getPropertiesList()).thenReturn(userProperties);

        EventFilter eventFilter = new EventFilter.Builder()
                .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                .withToTimestamp(testRunTimestamp)
                .withConnectionControl(connectionControl)
                .withUserProperties(userProperties)
                .build();

        //WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        //THEN
        assertTrue(result);
        verify(message).getPropertiesList();
    }

    @Test
    void GIVEN_user_properties_and_not_matched_filter_WHEN_is_matched_THEN_is_not() {
        final long testRunTimestamp = System.currentTimeMillis();
        final List<Mqtt5Properties> expectedUserProperties = Lists.newArrayList(
                Mqtt5Properties.newBuilder().setKey("type").setValue("json").build(),
                Mqtt5Properties.newBuilder().setKey("lang").setValue("ua").build()
        );

        final List<Mqtt5Properties> actualUserProperties = Lists.newArrayList(
                Mqtt5Properties.newBuilder().setKey("region").setValue("Asia").build(),
                Mqtt5Properties.newBuilder().setKey("lang").setValue("ua").build()
        );
        lenient().when(message.getPropertiesList()).thenReturn(actualUserProperties);

        EventFilter eventFilter = new EventFilter.Builder()
                .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                .withToTimestamp(testRunTimestamp)
                .withConnectionControl(connectionControl)
                .withUserProperties(expectedUserProperties)
                .build();

        //WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        //THEN
        assertFalse(result);
        verify(message).getPropertiesList();
    }

    @Test
    void GIVEN_user_properties_is_empty_and_matched_filter_WHEN_is_matched_THEN_is() {
        final long testRunTimestamp = System.currentTimeMillis();
        final List<Mqtt5Properties> userProperties = Lists.newArrayList(
                Mqtt5Properties.newBuilder().setKey("type").setValue("json").build(),
                Mqtt5Properties.newBuilder().setKey("lang").setValue("ua").build()
        );
        lenient().when(message.getPropertiesList()).thenReturn(userProperties);

        EventFilter eventFilter = new EventFilter.Builder()
                .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                .withToTimestamp(testRunTimestamp)
                .withConnectionControl(connectionControl)
                .build();

        //WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        //THEN
        assertTrue(result);
    }

    @Test
    void GIVEN_user_properties_is_not_empty_and_not_matched_filter_WHEN_is_matched_THEN_is_not() {
        final long testRunTimestamp = System.currentTimeMillis();
        final List<Mqtt5Properties> expectedUserProperties = Lists.newArrayList(
                Mqtt5Properties.newBuilder().setKey("type").setValue("json").build(),
                Mqtt5Properties.newBuilder().setKey("lang").setValue("ua").build()
        );

        lenient().when(message.getPropertiesList()).thenReturn(Collections.emptyList());

        EventFilter eventFilter = new EventFilter.Builder()
                .withType(Event.Type.EVENT_TYPE_MQTT_MESSAGE)
                .withToTimestamp(testRunTimestamp)
                .withConnectionControl(connectionControl)
                .withUserProperties(expectedUserProperties)
                .build();

        //WHEN
        boolean result = mqttMessageEvent.isMatched(eventFilter);

        //THEN
        assertFalse(result);
        verify(message).getPropertiesList();
    }
}
