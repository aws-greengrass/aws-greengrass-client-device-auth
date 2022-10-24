/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.infra;

import com.aws.greengrass.mqttclient.CallbackEventManager;
import com.aws.greengrass.mqttclient.MqttClient;
import com.aws.greengrass.testcommons.testutilities.GGExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;


@ExtendWith({MockitoExtension.class, GGExtension.class})
public class NetworkStateTest {
    @Mock
    MqttClient mqttClient;
    @Captor
    static ArgumentCaptor<CallbackEventManager.OnConnectCallback> onConnectCaptor;
    @Captor
    static ArgumentCaptor<MqttClientConnectionEvents> connectionEventsArgumentCaptor;
    private ForkJoinPool fjp = new ForkJoinPool();
    private NetworkState networkState;

    @BeforeEach
    void beforeEach() {
        fjp = new ForkJoinPool();
        networkState = new NetworkState(mqttClient, fjp);
        verify(mqttClient).addToCallbackEvents(onConnectCaptor.capture(), connectionEventsArgumentCaptor.capture());
    }

    @AfterEach
    void afterEach() {
        fjp.shutdown();
    }

    @ParameterizedTest
    @MethodSource("provideNetworkEventsAndExpectedState")
    void assertNetworkDomainEventOnNetworkEvent(
            Callable<NetworkState.ConnectionState> triggerNetworkChangeAndGetExpectedConnectionState) throws Exception {
        // GIVEN
        CountDownLatch cdl = new CountDownLatch(1);
        AtomicLong callbackThreadId = new AtomicLong();
        AtomicReference<NetworkState.ConnectionState> connState = new AtomicReference<>();
        long curThreadId = Thread.currentThread().getId();

        Consumer<NetworkState.ConnectionState> networkStateConsumer = (state) -> {
            connState.set(state);
            callbackThreadId.set(Thread.currentThread().getId());
            cdl.countDown();
        };
        networkState.registerHandler(networkStateConsumer);

        // WHEN
        NetworkState.ConnectionState expectedState = triggerNetworkChangeAndGetExpectedConnectionState.call();

        // THEN
        // Assert that NETWORK_UP event was emitted, and that it was emitted in a separate thread.
        // We're checking this because network change events are triggered in a CRT thread, which
        // we should not ever block
        assertTrue(cdl.await(1, TimeUnit.SECONDS));
        assertThat(callbackThreadId.get(), is(not(curThreadId)));
        assertThat(connState.get(), is(expectedState));
    }

    private static Stream<Arguments> provideNetworkEventsAndExpectedState() {
        return Stream.of(
                Arguments.arguments((Callable< NetworkState.ConnectionState>)() -> {
                    onConnectCaptor.getValue().onConnect(true);
                    return NetworkState.ConnectionState.NETWORK_UP;
                }),
                Arguments.arguments((Callable< NetworkState.ConnectionState>)() -> {
                    connectionEventsArgumentCaptor.getValue().onConnectionResumed(true);
                    return NetworkState.ConnectionState.NETWORK_UP;
                }),
                Arguments.arguments((Callable< NetworkState.ConnectionState>)() -> {
                    connectionEventsArgumentCaptor.getValue().onConnectionInterrupted(0);
                    return NetworkState.ConnectionState.NETWORK_DOWN;
                })
        );
    }
}
