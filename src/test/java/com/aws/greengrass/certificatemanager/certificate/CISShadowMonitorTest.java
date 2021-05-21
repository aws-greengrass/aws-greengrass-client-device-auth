/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.certificatemanager.certificate;

import com.aws.greengrass.cisclient.CISClient;
import com.aws.greengrass.mqttclient.MqttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.mqtt.MqttClientConnection;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;
import software.amazon.awssdk.crt.mqtt.QualityOfService;
import software.amazon.awssdk.iot.iotshadow.IotShadowClient;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowRequest;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowResponse;
import software.amazon.awssdk.iot.iotshadow.model.GetShadowSubscriptionRequest;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedEvent;
import software.amazon.awssdk.iot.iotshadow.model.ShadowDeltaUpdatedSubscriptionRequest;
import software.amazon.awssdk.iot.iotshadow.model.UpdateShadowRequest;

import java.security.KeyStoreException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static com.aws.greengrass.certificatemanager.certificate.CISShadowMonitor.SHADOW_GET_ACCEPTED_TOPIC;
import static com.aws.greengrass.certificatemanager.certificate.CISShadowMonitor.SHADOW_UPDATE_DELTA_TOPIC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class})
public class CISShadowMonitorTest {
    private static final String SHADOW_NAME = "testThing-gci";

    @Mock
    private IotShadowClient mockShadowClient;

    @Mock
    private MqttClientConnection mockConnection;

    @Mock
    private MqttClient mockMqttClient;

    @Mock
    private ExecutorService mockExecutor;

    @Mock
    private CISClient mockCISClient;

    @BeforeEach
    void setup() {
        lenient().doAnswer(invocation -> {
            ((Runnable)invocation.getArgument(0)).run();
            return null;
        }).when(mockExecutor).execute(any(Runnable.class));
        lenient().doAnswer(invocation -> {
            ((Runnable)invocation.getArgument(0)).run();
            return null;
        }).when(mockExecutor).submit(any(Runnable.class));
    }

    @Test
    public void GIVEN_CISShadowMonitor_WHEN_connected_THEN_publish_to_get_topic() {
        new CISShadowMonitor(mockMqttClient, mockConnection, mockShadowClient, mockExecutor, SHADOW_NAME, mockCISClient);
        ArgumentCaptor<MqttClientConnectionEvents> callbackArgumentCaptor = ArgumentCaptor.forClass(
                MqttClientConnectionEvents.class);
        verify(mockMqttClient, times(1)).addToCallbackEvents(callbackArgumentCaptor.capture());

        callbackArgumentCaptor.getValue().onConnectionResumed(false);
        ArgumentCaptor<GetShadowRequest> requestArgumentCaptor = ArgumentCaptor.forClass(GetShadowRequest.class);
        verify(mockShadowClient, times(1)).PublishGetShadow(requestArgumentCaptor.capture(),
                eq(QualityOfService.AT_LEAST_ONCE));
        assertThat(requestArgumentCaptor.getValue().thingName, is(SHADOW_NAME));
    }

    @Test
    public void GIVEN_CISShadowMonitor_WHEN_start_monitor_THEN_subscribe_and_publish_to_topics() {
        CISShadowMonitor cisShadowMonitor = new CISShadowMonitor(mockMqttClient, mockConnection, mockShadowClient,
                mockExecutor, SHADOW_NAME, mockCISClient);

        when(mockShadowClient.SubscribeToShadowDeltaUpdatedEvents(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        when(mockShadowClient.SubscribeToGetShadowAccepted(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        cisShadowMonitor.startMonitor();

        ArgumentCaptor<ShadowDeltaUpdatedSubscriptionRequest> updateSubscribeRequest = ArgumentCaptor.forClass(
                ShadowDeltaUpdatedSubscriptionRequest.class);
        ArgumentCaptor<GetShadowSubscriptionRequest> getSubscribeRequest = ArgumentCaptor.forClass(
                GetShadowSubscriptionRequest.class);
        ArgumentCaptor<GetShadowRequest> publishRequest = ArgumentCaptor.forClass(GetShadowRequest.class);

        verify(mockShadowClient, times(1)).SubscribeToShadowDeltaUpdatedEvents(
                updateSubscribeRequest.capture(), eq(QualityOfService.AT_LEAST_ONCE), any(),
                any());
        assertThat(updateSubscribeRequest.getValue().thingName, is(SHADOW_NAME));
        verify(mockShadowClient, times(1)).SubscribeToGetShadowAccepted(
                getSubscribeRequest.capture(), eq(QualityOfService.AT_LEAST_ONCE), any(),
                any());
        assertThat(getSubscribeRequest.getValue().thingName, is(SHADOW_NAME));

        verify(mockShadowClient, times(1)).PublishGetShadow(publishRequest.capture(),
                eq(QualityOfService.AT_LEAST_ONCE));
        assertThat(publishRequest.getValue().thingName, is(SHADOW_NAME));
    }

    @Test
    public void GIVEN_CISShadowMonitor_WHEN_update_delta_version_THEN_cert_generated() throws KeyStoreException {
        CISShadowMonitor cisShadowMonitor = new CISShadowMonitor(mockMqttClient, mockConnection, mockShadowClient,
                mockExecutor, SHADOW_NAME, mockCISClient);
        when(mockShadowClient.SubscribeToShadowDeltaUpdatedEvents(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        when(mockShadowClient.SubscribeToGetShadowAccepted(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        cisShadowMonitor.startMonitor();

        ArgumentCaptor<Consumer<ShadowDeltaUpdatedEvent>> updateSubscribeHandler = ArgumentCaptor.forClass(
                Consumer.class);
        verify(mockShadowClient, times(1)).SubscribeToShadowDeltaUpdatedEvents(any(), any(),
                updateSubscribeHandler.capture(), any());

        //receive new version
        CertificateGenerator mockCertGenerator = mock(CertificateGenerator.class);
        cisShadowMonitor.addToMonitor(mockCertGenerator);
        ShadowDeltaUpdatedEvent shadowDeltaUpdatedEvent = new ShadowDeltaUpdatedEvent();
        shadowDeltaUpdatedEvent.version = 1;
        updateSubscribeHandler.getValue().accept(shadowDeltaUpdatedEvent);

        verify(mockCertGenerator, times(1)).generateCertificate(any());
        ArgumentCaptor<UpdateShadowRequest> updateShadowRequest = ArgumentCaptor.forClass(UpdateShadowRequest.class);
        verify(mockShadowClient, times(1)).PublishUpdateShadow(updateShadowRequest.capture(),
                eq(QualityOfService.AT_LEAST_ONCE));
        assertThat(updateShadowRequest.getValue().version, is(1));

        //receive same version again
        reset(mockCertGenerator);
        reset(mockShadowClient);
        updateSubscribeHandler.getValue().accept(shadowDeltaUpdatedEvent);

        verify(mockCertGenerator, never()).generateCertificate(any());
        verify(mockShadowClient, times(1)).PublishUpdateShadow(updateShadowRequest.capture(),
                eq(QualityOfService.AT_LEAST_ONCE));
        assertThat(updateShadowRequest.getValue().version, is(1));
    }

    @Test
    public void GIVEN_CISShadowMonitor_WHEN_get_accepted_version_THEN_cert_generated() throws KeyStoreException {
        CISShadowMonitor cisShadowMonitor = new CISShadowMonitor(mockMqttClient, mockConnection, mockShadowClient,
                mockExecutor, SHADOW_NAME, mockCISClient);
        when(mockShadowClient.SubscribeToShadowDeltaUpdatedEvents(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        when(mockShadowClient.SubscribeToGetShadowAccepted(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        cisShadowMonitor.startMonitor();

        ArgumentCaptor<Consumer<GetShadowResponse>> getSubscribeHandler = ArgumentCaptor.forClass(
                Consumer.class);
        verify(mockShadowClient, times(1)).SubscribeToGetShadowAccepted(any(), any(),
                getSubscribeHandler.capture(), any());

        //receive new version
        CertificateGenerator mockCertGenerator = mock(CertificateGenerator.class);
        cisShadowMonitor.addToMonitor(mockCertGenerator);
        GetShadowResponse getShadowResponse = new GetShadowResponse();
        getShadowResponse.version = 1;
        getSubscribeHandler.getValue().accept(getShadowResponse);

        verify(mockCertGenerator, times(1)).generateCertificate(any());
        ArgumentCaptor<UpdateShadowRequest> updateShadowRequest = ArgumentCaptor.forClass(UpdateShadowRequest.class);
        verify(mockShadowClient, times(1)).PublishUpdateShadow(updateShadowRequest.capture(),
                eq(QualityOfService.AT_LEAST_ONCE));
        assertThat(updateShadowRequest.getValue().version, is(1));

        //receive same version again
        reset(mockCertGenerator);
        reset(mockShadowClient);
        getSubscribeHandler.getValue().accept(getShadowResponse);

        verify(mockCertGenerator, never()).generateCertificate(any());
        verify(mockShadowClient, times(1)).PublishUpdateShadow(updateShadowRequest.capture(),
                eq(QualityOfService.AT_LEAST_ONCE));
        assertThat(updateShadowRequest.getValue().version, is(1));
    }

    @Test
    public void GIVEN_CISShadowMonitor_WHEN_stop_monitor_THEN_unsubscribe_from_topics() {
        CISShadowMonitor cisShadowMonitor = new CISShadowMonitor(mockMqttClient, mockConnection, mockShadowClient,
                mockExecutor, SHADOW_NAME, mockCISClient);
        when(mockShadowClient.SubscribeToShadowDeltaUpdatedEvents(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        when(mockShadowClient.SubscribeToGetShadowAccepted(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));
        cisShadowMonitor.startMonitor();

        cisShadowMonitor.stopMonitor();
        ArgumentCaptor<String> unsubscribeTopic = ArgumentCaptor.forClass(String.class);
        verify(mockConnection, times(2)).unsubscribe(unsubscribeTopic.capture());
        assertThat(unsubscribeTopic.getAllValues(),
                containsInAnyOrder(String.format(SHADOW_UPDATE_DELTA_TOPIC, SHADOW_NAME),
                        String.format(SHADOW_GET_ACCEPTED_TOPIC, SHADOW_NAME)));
    }
}
