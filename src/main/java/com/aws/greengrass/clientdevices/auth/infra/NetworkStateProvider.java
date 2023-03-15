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

public interface NetworkStateProvider {
    enum ConnectionState {
        NETWORK_UP, NETWORK_DOWN,
    }

    void registerHandler(Consumer<ConnectionState> networkChangeHandler);

    ConnectionState getConnectionState();


    class Default implements NetworkStateProvider {
        private final ExecutorService executorService;

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
         *
         * @param mqttClient      MqttClient wrapper.
         * @param executorService Executor service used to dispatch connection state change events.
         */
        @Inject
        public Default(MqttClient mqttClient, ExecutorService executorService) {
            this.mqttClient = mqttClient;
            mqttClient.addToCallbackEvents(onConnect, callbacks);
            this.executorService = executorService;
        }

        @Override
        public void registerHandler(Consumer<NetworkStateProvider.ConnectionState> networkChangeHandler) {
            handlers.add(networkChangeHandler);
        }

        /**
         * Returns the current state of the Greengrass MQTT connection.
         * </p>
         * The idea here is to use the Greengrass MQTT connection as a proxy for whether we think the core device is
         * online. This may be useful in latency sensitive situation where we'd like to avoid making a network call if
         * we think it will time out.
         * </p>
         * Note that this is not a perfect indicator. Assume the response could be wrong and make sure your code will
         * function in case this never returns NETWORK_UP.
         *
         * @return Connection state enum
         */
        @Override
        public NetworkStateProvider.ConnectionState getConnectionState() {
            if (mqttClient.getMqttOnline().get()) {
                return NetworkStateProvider.ConnectionState.NETWORK_UP;
            } else {
                return NetworkStateProvider.ConnectionState.NETWORK_DOWN;
            }
        }

        private void emitNetworkUp() {
            if (isRunning()) {
                executorService.submit(() -> {
                    for (Consumer<NetworkStateProvider.ConnectionState> handler : handlers) {
                        handler.accept(NetworkStateProvider.ConnectionState.NETWORK_UP);
                    }
                });
            }
        }

        private void emitNetworkDown() {
            if (isRunning()) {
                executorService.submit(() -> {
                    for (Consumer<NetworkStateProvider.ConnectionState> handler : handlers) {
                        handler.accept(NetworkStateProvider.ConnectionState.NETWORK_DOWN);
                    }
                });
            }
        }

        // TODO: we do not need to listen for network events, or emit network events when
        //  CDA is not running. For now, just assume we're always running.
        private boolean isRunning() {
            return true;
        }
    }

}
