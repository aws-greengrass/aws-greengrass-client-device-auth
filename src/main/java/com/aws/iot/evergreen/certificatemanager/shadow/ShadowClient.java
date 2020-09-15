/* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0 */

package com.aws.iot.evergreen.certificatemanager.shadow;

import com.aws.iot.evergreen.mqtt.MqttClient;
import com.aws.iot.evergreen.mqtt.PublishRequest;
import com.aws.iot.evergreen.mqtt.SubscribeRequest;
import lombok.NonNull;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;
import software.amazon.awssdk.crt.mqtt.MqttMessage;
import software.amazon.awssdk.crt.mqtt.QualityOfService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class ShadowClient {

    private final ShadowCallbacks shadowCallbacks;

    private final MqttClient mqttClient;

    /**
     * Constructor to create ShadowClient.
     *
     * @param mqttClient mqtt client to use for shadow updates
     * @param shadowCallbacks callback methods
     */
    public ShadowClient(MqttClient mqttClient, ShadowCallbacks shadowCallbacks) {
        this.mqttClient = mqttClient;
        this.shadowCallbacks = shadowCallbacks;

        MqttClientConnectionEvents mqttClientConnectionEvents = new MqttClientConnectionEvents() {
            @Override
            public void onConnectionInterrupted(int errorCode) {
            }

            @Override
            public void onConnectionResumed(boolean sessionPresent) {
                shadowCallbacks.onConnect();
            }
        };
        this.mqttClient.addToCallbackEvents(mqttClientConnectionEvents);
    }

    /**
     * Subscribes to the given list of topics.
     * @param topics list of topics
     * @throws ExecutionException   if an error occurs
     * @throws InterruptedException if the thread is interrupted while subscribing
     * @throws TimeoutException     if the request times out
     */
    public void subscribeToShadows(@NonNull List<String> topics)
            throws InterruptedException, ExecutionException, TimeoutException {
        for (String topic : topics) {
            mqttClient.subscribe(SubscribeRequest.builder().topic(topic).qos(QualityOfService.AT_LEAST_ONCE)
                    .callback(shadowCallbacks::onMessage).build());
            //no exception means topic has been subscribed
            shadowCallbacks.onSubscribe(topic);
        }
    }

    /**
     * Publishes to given topic with payload.
     * @param topic topic to publish on
     * @param payload payload to publish
     * @return CompletableFuture
     */
    public CompletableFuture<Integer> updateShadow(@NonNull String topic, @NonNull byte[] payload) {
        return mqttClient.publish(
                PublishRequest.builder().payload(payload).topic(topic).qos(QualityOfService.AT_LEAST_ONCE).build());
    }

    public interface ShadowCallbacks {
        void onConnect();

        void onSubscribe(String topic);

        void onMessage(MqttMessage message);
    }
}
