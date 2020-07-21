/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm.shadow;

import com.aws.iot.evergreen.mqtt.MqttClient;
import com.aws.iot.evergreen.mqtt.PublishRequest;
import com.aws.iot.evergreen.mqtt.SubscribeRequest;
import com.aws.iot.evergreen.testcommons.testutilities.EGExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@ExtendWith({MockitoExtension.class, EGExtension.class})
public class ShadowClientTest extends EGExtension {
    @Mock
    private MqttClient mockMqttClient;

    @Mock
    private ShadowClient.ShadowCallbacks mockShadowCallbacks;

    ShadowClient client;

    @BeforeEach
    public void setup() {
        client = new ShadowClient(mockMqttClient, mockShadowCallbacks);
    }

    @Test
    public void GIVEN_shadow_client_WHEN_subscribeToShadows_called_THEN_subscribes_with_mqtt_client()
            throws InterruptedException, ExecutionException, TimeoutException {
        ArgumentCaptor<SubscribeRequest> subscribeRequestArgumentCaptor =
                ArgumentCaptor.forClass(SubscribeRequest.class);
        doNothing().when(mockMqttClient).subscribe(subscribeRequestArgumentCaptor.capture());

        List<String> topics = new ArrayList<String>() {{
            add(UUID.randomUUID().toString());
            add(UUID.randomUUID().toString());
        }};
        client.subscribeToShadows(topics);

        assertThat(subscribeRequestArgumentCaptor.getAllValues().size(), is(2));
        assertThat(subscribeRequestArgumentCaptor.getAllValues().get(0).getTopic(), is(topics.get(0)));
        assertThat(subscribeRequestArgumentCaptor.getAllValues().get(1).getTopic(), is(topics.get(1)));
        assertThat(subscribeRequestArgumentCaptor.getAllValues().get(1).getQos(), is(QualityOfService.AT_LEAST_ONCE));
    }

    @Test
    public void GIVEN_shadow_client_WHEN_subscribeToShadows_called_and_mqtt_client_fails_THEN_throws_exception()
            throws InterruptedException, ExecutionException, TimeoutException {
        doThrow(new TimeoutException()).when(mockMqttClient).subscribe(ArgumentMatchers.any(SubscribeRequest.class));

        List<String> topics = new ArrayList<String>() {{
            add(UUID.randomUUID().toString());
        }};
        Assertions.assertThrows(TimeoutException.class, () -> client.subscribeToShadows(topics));
    }

    @Test
    public void GIVEN_shadow_client_subscribed_to_shadows_WHEN_gets_message_THEN_calls_callback()
            throws InterruptedException, ExecutionException, TimeoutException {
        ArgumentCaptor<SubscribeRequest> subscribeRequestArgumentCaptor =
                ArgumentCaptor.forClass(SubscribeRequest.class);
        doNothing().when(mockMqttClient).subscribe(subscribeRequestArgumentCaptor.capture());

        List<String> topics = new ArrayList<String>() {{
            add(UUID.randomUUID().toString());
        }};
        client.subscribeToShadows(topics);

        ArgumentCaptor<MqttMessage> mqttMessageArgumentCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        doNothing().when(mockShadowCallbacks).onMessage(mqttMessageArgumentCaptor.capture());

        MqttMessage message = new MqttMessage(topics.get(0), "payload".getBytes());
        SubscribeRequest request = subscribeRequestArgumentCaptor.getValue();
        request.getCallback().accept(message);

        assertThat(mqttMessageArgumentCaptor.getAllValues().size(), is(1));
        assertThat(mqttMessageArgumentCaptor.getValue(), is(message));
    }

    @Test
    public void GIVEN_shadow_client_WHEN_updateShadow_called_THEN_publish_with_mqtt_client()
            throws InterruptedException, ExecutionException, TimeoutException {
        ArgumentCaptor<PublishRequest> publishRequestArgumentCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        doNothing().when(mockMqttClient).publish(publishRequestArgumentCaptor.capture());

        String topic = UUID.randomUUID().toString();
        byte[] payload = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        client.updateShadow(topic, payload);

        assertThat(publishRequestArgumentCaptor.getAllValues().size(), is(1));
        assertThat(publishRequestArgumentCaptor.getValue().getTopic(), is(topic));
        assertThat(publishRequestArgumentCaptor.getValue().getPayload(), is(payload));
    }

    @Test
    public void GIVEN_shadow_client_WHEN_updateShadow_called_and_mqtt_client_fails_THEN_throws_exception()
            throws InterruptedException, ExecutionException, TimeoutException {
        doThrow(new TimeoutException()).when(mockMqttClient).publish(ArgumentMatchers.any(PublishRequest.class));

        String topic = UUID.randomUUID().toString();
        byte[] payload = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        Assertions.assertThrows(TimeoutException.class, () -> client.updateShadow(topic, payload));
    }
}
