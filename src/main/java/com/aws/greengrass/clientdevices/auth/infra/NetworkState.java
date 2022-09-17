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
import java.util.function.Consumer;
import javax.inject.Inject;

public final class NetworkState {
    public static final NetworkConnectivityState NETWORK_UP = NetworkConnectivityState.NETWORK_UP;
    public static final NetworkConnectivityState NETWORK_DOWN = NetworkConnectivityState.NETWORK_DOWN;

    public enum NetworkConnectivityState {
        NETWORK_UP,
        NETWORK_DOWN,
    }

    private final MqttClient mqttClient;
    private final List<Consumer<NetworkConnectivityState>> handlers = new ArrayList<>();

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
     * @param mqttClient   MqttClient wrapper.
     */
    @Inject
    public NetworkState(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
        mqttClient.addToCallbackEvents(onConnect, callbacks);
    }

    public void registerHandler(Consumer<NetworkConnectivityState> networkChangeHandler) {
        handlers.add(networkChangeHandler);
    }

    /**
     * Get connectivity state of the Greengrass MQTT connection. <b>This may not be
     * a reliable indicator for whether Greengrass has HTTP network connectivity.</b>
     * Similarly, MQTT connectivity may not indicate HTTP connectivity.
     * This may be useful in latency sensitive situations to decide if a network
     * call should be attempted. However, use it with caution. Assume the information
     * provided by this method could be wrong, and give yourself a way to recover in
     * case it's always wrong.
     *
     * @return True if Greengrass has MQTT connectivity, else false
     */
    public NetworkConnectivityState getMqttConnectivityState() {
        if (mqttClient.connected()) {
            return NETWORK_UP;
        } else {
            return NETWORK_DOWN;
        }
    }

    private void emitNetworkUp() {
        if (isRunning()) {
            for (Consumer<NetworkConnectivityState> handler : handlers) {
                handler.accept(NETWORK_UP);
            }
        }
    }

    private void emitNetworkDown() {
        if (isRunning()) {
            for (Consumer<NetworkConnectivityState> handler : handlers) {
                handler.accept(NETWORK_DOWN);
            }
        }
    }

    // TODO: we do not need to listen for network events, or emit network events when
    //  CDA is not running. For now, just assume we're always running.
    private boolean isRunning() {
        return true;
    }
}
