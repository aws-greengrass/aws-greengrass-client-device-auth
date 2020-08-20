/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.iot.evergreen.dcm;

import com.aws.iot.evergreen.dcm.shadow.ShadowClient;
import com.aws.iot.evergreen.deployment.exceptions.AWSIotException;
import com.aws.iot.evergreen.iot.IotCloudHelper;
import com.aws.iot.evergreen.iot.IotConnectionManager;
import com.aws.iot.evergreen.iot.model.IotCloudResponse;
import com.aws.iot.evergreen.mqtt.MqttClient;
import com.aws.iot.evergreen.testcommons.testutilities.EGExtension;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.mqtt.MqttMessage;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class})
public class VersionAndNetworkUpdateManagerTest extends EGExtension {
    @Mock
    private IotConnectionManager mockIotConnectionManager;

    @Mock
    private IotCloudHelper mockIotCloudHelper;

    @Mock
    private ShadowClient mockShadowClient;

    @Mock
    private MqttClient mockMqttClient;

    @Mock
    private VersionAndNetworkUpdateManager.UpdateHandler mockUpdateHandler;

    private static final String HTTP_ENDPOINT = UUID.randomUUID().toString();
    private static final String THING_NAME = UUID.randomUUID().toString();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    VersionAndNetworkUpdateManager listener;

    @BeforeEach
    public void setup() {
    }

    @Test
    public void GIVEN_version_and_network_change_listener_WHEN_start_called_THEN_subscribes_to_shadow_updates()
            throws InterruptedException, ExecutionException, TimeoutException {
        listener = new VersionAndNetworkUpdateManager(mockIotConnectionManager, mockIotCloudHelper, HTTP_ENDPOINT,
                THING_NAME, mockUpdateHandler, mockShadowClient);
        ArgumentCaptor<List<String>> topicsCaptor = ArgumentCaptor.forClass(List.class);
        doNothing().when(mockShadowClient).subscribeToShadows(topicsCaptor.capture());
        listener.start();

        assertThat(topicsCaptor.getAllValues().size(), is(1));
        assertThat(topicsCaptor.getValue().size(), is(1));
    }

    @Test
    public void GIVEN_version_and_network_change_listener_WHEN_start_called_and_shadow_client_fails_THEN_throws()
            throws InterruptedException, ExecutionException, TimeoutException {
        listener = new VersionAndNetworkUpdateManager(mockIotConnectionManager, mockIotCloudHelper, HTTP_ENDPOINT,
                THING_NAME, mockUpdateHandler, mockShadowClient);
        ArgumentCaptor<List<String>> topicsCaptor = ArgumentCaptor.forClass(List.class);
        doThrow(new TimeoutException()).when(mockShadowClient).subscribeToShadows(topicsCaptor.capture());
        Assertions.assertThrows(TimeoutException.class, () -> listener.start());
    }

    @Test
    public void GIVEN_version_and_network_change_listener_WHEN_on_connect_callback_THEN_calls_on_network_reconnect_callback() {
        listener = new VersionAndNetworkUpdateManager(mockIotConnectionManager, mockIotCloudHelper, HTTP_ENDPOINT,
                THING_NAME, mockUpdateHandler, mockMqttClient);
        ShadowClient.ShadowCallbacks shadowCallbacks = listener.getShadowCallbacks();
        // Simulate shadow onConnect callback
        shadowCallbacks.onConnect();

        verify(mockUpdateHandler, times(1)).handleNetworkReconnect();
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.CIS_SERVICE_NAME})
    public void GIVEN_version_change_listener_WHEN_on_subscribe_callback_THEN_calls_on_version_changed_callback(
            String service) throws JsonProcessingException, AWSIotException, ExecutionException, TimeoutException,
            InterruptedException {
        listener = new VersionAndNetworkUpdateManager(mockIotConnectionManager, mockIotCloudHelper, HTTP_ENDPOINT,
                THING_NAME, mockUpdateHandler, mockMqttClient);
        ShadowClient.ShadowCallbacks shadowCallbacks = listener.getShadowCallbacks();

        GetShadowVersionResponse response = new GetShadowVersionResponse(new GetShadowVersionResponse.State(
                new GetShadowVersionResponse.State.Delta(UUID.randomUUID().toString())));

        String httpResponse = OBJECT_MAPPER.writeValueAsString(response);
        IotCloudResponse cloudResponse = new IotCloudResponse(httpResponse.getBytes(), 200);
        doReturn(cloudResponse).when(mockIotCloudHelper)
                .sendHttpRequest(any(), eq(listener.getShadowUrl(service)), any(), eq(null));

        lenient().doReturn(CompletableFuture.completedFuture(null)).when(mockUpdateHandler)
                .handleServiceVersionUpdate(anyString());

        // Simulate shadow onConnect callback
        shadowCallbacks.onSubscribe(listener.getShadowDeltaTopic(service));

        verify(mockUpdateHandler, times(1)).handleServiceVersionUpdate(eq(service));
        verify(mockMqttClient, times(1)).publish(any());

        // This time, send callback for a different version
        response = new GetShadowVersionResponse(new GetShadowVersionResponse.State(
                new GetShadowVersionResponse.State.Delta(UUID.randomUUID().toString())));

        httpResponse = OBJECT_MAPPER.writeValueAsString(response);
        cloudResponse = new IotCloudResponse(httpResponse.getBytes(), 200);
        doReturn(cloudResponse).when(mockIotCloudHelper)
                .sendHttpRequest(any(), eq(listener.getShadowUrl(service)), any(), eq(null));

        reset(mockUpdateHandler);
        reset(mockMqttClient);
        lenient().doReturn(CompletableFuture.completedFuture(null)).when(mockUpdateHandler)
                .handleServiceVersionUpdate(anyString());
        // Simulate shadow onConnect callback
        shadowCallbacks.onSubscribe(listener.getShadowDeltaTopic(service));

        verify(mockUpdateHandler, times(1)).handleServiceVersionUpdate(eq(service));
        verify(mockMqttClient, times(1)).publish(any());

        // This time, send callback for the previous version
        reset(mockUpdateHandler);
        reset(mockMqttClient);
        // Simulate shadow onConnect callback
        shadowCallbacks.onSubscribe(listener.getShadowDeltaTopic(service));

        verify(mockUpdateHandler, times(0)).handleServiceVersionUpdate(anyString());
        // The version is still reported back
        verify(mockMqttClient, times(1)).publish(any());

        // This time, send callback for an empty version
        response = new GetShadowVersionResponse(
                new GetShadowVersionResponse.State(new GetShadowVersionResponse.State.Delta("")));

        httpResponse = OBJECT_MAPPER.writeValueAsString(response);
        cloudResponse = new IotCloudResponse(httpResponse.getBytes(), 200);
        doReturn(cloudResponse).when(mockIotCloudHelper)
                .sendHttpRequest(any(), eq(listener.getShadowUrl(service)), any(), eq(null));

        reset(mockUpdateHandler);
        reset(mockMqttClient);
        // Simulate shadow onConnect callback
        shadowCallbacks.onSubscribe(listener.getShadowDeltaTopic(service));

        verify(mockUpdateHandler, times(0)).handleServiceVersionUpdate(anyString());
        verify(mockMqttClient, times(0)).publish(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.CIS_SERVICE_NAME})
    public void GIVEN_version_change_listener_WHEN_on_subscribe_callback_iot_call_fails_THEN_no_op(String service)
            throws AWSIotException, ExecutionException, TimeoutException, InterruptedException {
        listener = new VersionAndNetworkUpdateManager(mockIotConnectionManager, mockIotCloudHelper, HTTP_ENDPOINT,
                THING_NAME, mockUpdateHandler, mockMqttClient);
        ShadowClient.ShadowCallbacks shadowCallbacks = listener.getShadowCallbacks();

        doThrow(new AWSIotException("")).when(mockIotCloudHelper)
                .sendHttpRequest(any(), eq(listener.getShadowUrl(service)), any(), eq(null));

        // Simulate shadow onConnect callback
        shadowCallbacks.onSubscribe(listener.getShadowDeltaTopic(service));

        verify(mockUpdateHandler, times(0)).handleServiceVersionUpdate(anyString());
        verify(mockMqttClient, times(0)).publish(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.CIS_SERVICE_NAME})
    public void GIVEN_version_change_listener_WHEN_on_subscribe_callback_iot_returns_invalid_response_THEN_no_op(
            String service) throws AWSIotException, ExecutionException, TimeoutException, InterruptedException {
        listener = new VersionAndNetworkUpdateManager(mockIotConnectionManager, mockIotCloudHelper, HTTP_ENDPOINT,
                THING_NAME, mockUpdateHandler, mockMqttClient);
        ShadowClient.ShadowCallbacks shadowCallbacks = listener.getShadowCallbacks();

        IotCloudResponse cloudResponse = new IotCloudResponse("foo".getBytes(), 200);
        doReturn(cloudResponse).when(mockIotCloudHelper)
                .sendHttpRequest(any(), eq(listener.getShadowUrl(service)), any(), eq(null));

        // Simulate shadow onConnect callback
        shadowCallbacks.onSubscribe(listener.getShadowDeltaTopic(service));

        verify(mockUpdateHandler, times(0)).handleServiceVersionUpdate(anyString());
        verify(mockMqttClient, times(0)).publish(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.CIS_SERVICE_NAME})
    public void GIVEN_version_change_listener_WHEN_on_message_callback_THEN_calls_on_version_changed_callback(
            String service) throws JsonProcessingException, ExecutionException, TimeoutException, InterruptedException {
        listener = new VersionAndNetworkUpdateManager(mockIotConnectionManager, mockIotCloudHelper, HTTP_ENDPOINT,
                THING_NAME, mockUpdateHandler, mockMqttClient);
        ShadowClient.ShadowCallbacks shadowCallbacks = listener.getShadowCallbacks();

        ShadowDeltaMessage message = new ShadowDeltaMessage(new ShadowDeltaMessage.State(UUID.randomUUID().toString()));

        lenient().doReturn(CompletableFuture.completedFuture(null)).when(mockUpdateHandler)
                .handleServiceVersionUpdate(anyString());

        // Simulate shadow onMessage callback
        shadowCallbacks.onMessage(
                new MqttMessage(listener.getShadowDeltaTopic(service), OBJECT_MAPPER.writeValueAsBytes(message)));

        verify(mockUpdateHandler, times(1)).handleServiceVersionUpdate(anyString());
        verify(mockMqttClient, times(1)).publish(any());

        // This time, send callback for a different version
        reset(mockUpdateHandler);
        reset(mockMqttClient);
        lenient().doReturn(CompletableFuture.completedFuture(null)).when(mockUpdateHandler)
                .handleServiceVersionUpdate(anyString());

        message = new ShadowDeltaMessage(new ShadowDeltaMessage.State(UUID.randomUUID().toString()));
        // Simulate shadow onMessage callback
        shadowCallbacks.onMessage(
                new MqttMessage(listener.getShadowDeltaTopic(service), OBJECT_MAPPER.writeValueAsBytes(message)));

        verify(mockUpdateHandler, times(1)).handleServiceVersionUpdate(anyString());
        verify(mockMqttClient, times(1)).publish(any());

        // This time, send callback for the previous version
        reset(mockUpdateHandler);
        reset(mockMqttClient);
        lenient().doReturn(CompletableFuture.completedFuture(null)).when(mockUpdateHandler)
                .handleServiceVersionUpdate(anyString());

        // Simulate shadow onMessage callback
        shadowCallbacks.onMessage(
                new MqttMessage(listener.getShadowDeltaTopic(service), OBJECT_MAPPER.writeValueAsBytes(message)));

        verify(mockUpdateHandler, times(0)).handleServiceVersionUpdate(anyString());
        // The version is still reported back
        verify(mockMqttClient, times(1)).publish(any());

        // This time, send callback for an empty version
        reset(mockUpdateHandler);
        reset(mockMqttClient);
        lenient().doReturn(CompletableFuture.completedFuture(null)).when(mockUpdateHandler)
                .handleServiceVersionUpdate(anyString());

        message = new ShadowDeltaMessage(new ShadowDeltaMessage.State(""));
        shadowCallbacks.onMessage(
                new MqttMessage(listener.getShadowDeltaTopic(service), OBJECT_MAPPER.writeValueAsBytes(message)));

        verify(mockUpdateHandler, times(0)).handleServiceVersionUpdate(anyString());
        verify(mockMqttClient, times(0)).publish(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.CIS_SERVICE_NAME})
    public void GIVEN_version_change_listener_WHEN_on_message_callback_with_invalid_response_THEN_no_op(String service)
            throws ExecutionException, TimeoutException, InterruptedException {
        listener = new VersionAndNetworkUpdateManager(mockIotConnectionManager, mockIotCloudHelper, HTTP_ENDPOINT,
                THING_NAME, mockUpdateHandler, mockMqttClient);
        ShadowClient.ShadowCallbacks shadowCallbacks = listener.getShadowCallbacks();

        // Simulate shadow onMessage callback
        shadowCallbacks.onMessage(new MqttMessage(listener.getShadowDeltaTopic(service), "foo".getBytes()));

        verify(mockUpdateHandler, times(0)).handleServiceVersionUpdate(anyString());
        verify(mockMqttClient, times(0)).publish(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {Constants.CIS_SERVICE_NAME})
    public void GIVEN_version_change_listener_WHEN_handle_service_version_update_fails_THEN_no_op(String service)
            throws JsonProcessingException, AWSIotException, ExecutionException, TimeoutException,
            InterruptedException {
        listener = new VersionAndNetworkUpdateManager(mockIotConnectionManager, mockIotCloudHelper, HTTP_ENDPOINT,
                THING_NAME, mockUpdateHandler, mockMqttClient);
        ShadowClient.ShadowCallbacks shadowCallbacks = listener.getShadowCallbacks();

        GetShadowVersionResponse response = new GetShadowVersionResponse(new GetShadowVersionResponse.State(
                new GetShadowVersionResponse.State.Delta(UUID.randomUUID().toString())));

        String httpResponse = OBJECT_MAPPER.writeValueAsString(response);
        IotCloudResponse cloudResponse = new IotCloudResponse(httpResponse.getBytes(), 200);
        doReturn(cloudResponse).when(mockIotCloudHelper)
                .sendHttpRequest(any(), eq(listener.getShadowUrl(service)), any(), eq(null));

        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(new AWSIotException(""));
        doReturn(future).when(mockUpdateHandler).handleServiceVersionUpdate(anyString());

        // Simulate shadow onConnect callback
        shadowCallbacks.onSubscribe(listener.getShadowDeltaTopic(service));

        verify(mockUpdateHandler, times(1)).handleServiceVersionUpdate(eq(service));
        verify(mockMqttClient, times(0)).publish(any());
    }
}
