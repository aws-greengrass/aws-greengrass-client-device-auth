/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.clientdevices.auth.infra;

import com.aws.greengrass.mqttclient.CallbackEventManager;
import com.aws.greengrass.mqttclient.MqttClient;
import software.amazon.awssdk.crt.mqtt.MqttClientConnectionEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.inject.Inject;

public final class NetworkState {
    private final ExecutorService executorService;

    public enum ConnectionState {
        NETWORK_UP,
        NETWORK_DOWN,
    }

    private final MqttClient mqttClient;
    private final List<Consumer<ConnectionState>> handlers = new ArrayList<>();

    private final CallbackEventManager.OnConnectCallback onConnect = curSessionPresent -> {
        emitNetworkUp();
    };

    private final MqttClientConnectionEvents callbacks = new MqttClientConnectionEvents() {
        @Override
        public void onConnectionInterrupted(int errorCode) {
            emitNetworkDown();
        }

        @Override
        public void onConnectionResumed(boolean sessionPresent) {
            emitNetworkUp();
        }
    };

    /**
     * Represents the current network state.
     * @param mqttClient      MqttClient wrapper.
     * @param executorService Executor service used to dispatch connection state change events.
     */
    @Inject
    public NetworkState(MqttClient mqttClient, ExecutorService executorService) {
        this.mqttClient = mqttClient;
        mqttClient.addToCallbackEvents(onConnect, callbacks);
        this.executorService = executorService;
    }

    public void registerHandler(Consumer<ConnectionState> networkChangeHandler) {
        handlers.add(networkChangeHandler);
    }

    /**
     * Returns the believed Greengrass connection state using the MQTT
     * connection as a proxy indicator.
     * </p>
     * NETWORK_UP and NETWORK_DOWN are really euphemisms for whether there is
     * a network route to IoT Core. With that out of the way, understand that
     * the information returned by this method is squishy, at best. The only
     * way to know for sure if a network call will succeed is to try it.
     * </p>
     * You can consider using this method in latency sensitive situations to
     * decide if an HTTP call should be attempted now or queued as a background
     * task, but you shouldn't require a NETWORK_UP response before at least
     * trying. Assume the response could be wrong, and give yourself a way
     * to recover in case this never returns NETWORK_UP.
     * </p>
     * In other words, this is a footgun. Treat it as such.
     *
     * @return Connection state enum
     */
    public ConnectionState getConnectionStateFromMqtt() {
        if (mqttClient.connected()) {
            return ConnectionState.NETWORK_UP;
        } else {
            return ConnectionState.NETWORK_DOWN;
        }
    }

    private void emitNetworkUp() {
        if (isRunning()) {
            for (Consumer<ConnectionState> handler : handlers) {
                executorService.submit(() -> handler.accept(ConnectionState.NETWORK_UP));
            }
        }
    }

    private void emitNetworkDown() {
        if (isRunning()) {
            for (Consumer<ConnectionState> handler : handlers) {
                executorService.submit(() -> handler.accept(ConnectionState.NETWORK_DOWN));
            }
        }
    }

    // TODO: we do not need to listen for network events, or emit network events when
    //  CDA is not running. For now, just assume we're always running.
    private boolean isRunning() {
        return true;
    }
}
