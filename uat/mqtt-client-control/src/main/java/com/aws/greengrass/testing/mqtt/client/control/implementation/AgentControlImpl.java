/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation;

import com.aws.greengrass.testing.mqtt.client.Mqtt5ConnAck;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.MqttClientControlGrpc;
import com.aws.greengrass.testing.mqtt.client.MqttCloseRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectReply;
import com.aws.greengrass.testing.mqtt.client.MqttConnectRequest;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttPublishRequest;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.MqttUnsubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.ShutdownRequest;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of AgentControl.
 */
public class AgentControlImpl implements AgentControl {
    private static final int DEFAULT_DISCONNECT_REASON = 4;

    private static final Logger logger = LogManager.getLogger(AgentControlImpl.class);

    private final AtomicBoolean isShutdownSent = new AtomicBoolean(false);
    private final AtomicBoolean isDisconnected = new AtomicBoolean(false);
    private final ConcurrentHashMap<Integer, ConnectionControlImpl> connections = new ConcurrentHashMap<>();


    // That lock prevent cases when messages are received but connection not yet registered.
    private final Lock connectLock = new ReentrantLock();

    private final String agentId;
    private final String address;
    private final int port;
    private int timeout = DEFAULT_TIMEOUT;

    private ManagedChannel channel;
    private MqttClientControlGrpc.MqttClientControlBlockingStub blockingStub;


    /**
     * Creates instanse of AgentControlImpl.
     *
     * @param agentId id of agent
     * @param address address of gRPC server of agent (MQTT client)
     * @param port port of  gRPC server of agent (MQTT client)
     */
    AgentControlImpl(String agentId, String address, int port) {
        super();
        this.agentId = agentId;
        this.address = address;
        this.port = port;
    }


    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * Called when MQTT message has been received.
     *
     * @param connectionId id of connected where receives message
     * @param message the received MQTT message
     */
    public void onMessageReceived(int connectionId, Mqtt5Message message) {
        ConnectionControlImpl connectionControl;
        try {
            connectLock.lock();
            connectionControl = connections.get(connectionId);
        } finally {
            connectLock.unlock();
        }

        if (connectionControl == null) {
            logger.atWarn().log("Message received but connection with id {} could not found", connectionId);
        } else {
            connectionControl.onMessageReceived(message);
        }
    }

    /**
     * Called when MQTT connection has been disconnected.
     *
     * @param connectionId id of connected where receives message
     * @param disconnect optional infomation from DISCONNECT packet
     * @param error optional OS-dependent error string
     */
    public void onMqttDisconnect(int connectionId, Mqtt5Disconnect disconnect, String error) {
        ConnectionControlImpl connectionControl = connections.get(connectionId);
        if (connectionControl == null) {
            logger.atWarn().log("MQTT disconnect received but connection with id {} could not found", connectionId);
        } else {
            // NOTE: connectionControl is not unregistered until closeMqttConnection() explicitly called
            connectionControl.onMqttDisconnect(disconnect, error);
        }
    }

    @Override
    public void startAgent() {
        this.channel = Grpc.newChannelBuilderForAddress(address, port, InsecureChannelCredentials.create()).build();
        this.blockingStub = MqttClientControlGrpc.newBlockingStub(this.channel);
    }

    @Override
    public void stopAgent() {
        closeAllMqttConnections();
        disconnect();
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public void shutdownAgent(String reason) {
        ShutdownRequest request = ShutdownRequest.newBuilder().setReason(reason).build();
        blockingStub.shutdownAgent(request);
        logger.atInfo().log("shutdown request sent successfully");
        isShutdownSent.set(true);
    }

    @Override
    public ConnectionControl createMqttConnection(@NonNull MqttConnectRequest connectRequest,
                                                    @NonNull ConnectionEvents connectionEvents) {
        ConnectionControlImpl connectionControl;
        try {
            connectLock.lock();
            MqttConnectReply response = blockingStub.createMqttConnection(connectRequest);
            if (response.getConnected()) {
                int connectionId = response.getConnectionId().getConnectionId();
                connectionControl = new ConnectionControlImpl(response, connectionEvents, this);
                connections.put(connectionId, connectionControl);
                logger.atInfo().log("Created connection with id {} CONNACK '{}'", connectionId, response.getConnAck());
            } else {
                String error = response.getError();
                Mqtt5ConnAck connAck = response.getConnAck();
                logger.atError().log("Couldn't create MQTT connection error '{}' CONNACK '{}'", error, connAck);
                throw new RuntimeException("Couldn't create MQTT connection: " + error);
            }
        } finally {
            connectLock.unlock();
        }
        logger.atInfo().log("createMqttConnection: MQTT connectionId {} created", connectionControl.getConnectionId());
        return connectionControl;
    }

    /**
     * Close MQTT connection to the broker.
     *
     * @param closeRequest parameters of MQTT disconnect
     * @throws StatusRuntimeException on errors
     */
    public void closeMqttConnection(MqttCloseRequest closeRequest) {
        blockingStub.closeMqttConnection(closeRequest);
        int connectionId = closeRequest.getConnectionId().getConnectionId();
        connections.remove(connectionId);
        logger.atInfo().log("closeMqttConnection: MQTT connectionId {} closed", connectionId);
    }

    /**
     * Do MQTT subscription(s).
     *
     * @param subscribeRequest subscribe request
     * @return reply to subscribe
     * @throws StatusRuntimeException on errors
     */
    public MqttSubscribeReply subscribeMqtt(MqttSubscribeRequest subscribeRequest) {
        int connectionId = subscribeRequest.getConnectionId().getConnectionId();
        logger.atInfo().log("SubscribeMqtt: subscribe on connection {}", connectionId);
        return blockingStub.subscribeMqtt(subscribeRequest);
    }

    /**
     * Remove MQTT subscription(s).
     *
     * @param unsubscribeRequest unsubscribe request
     * @return reply to unsubscribe
     * @throws StatusRuntimeException on errors
     */
    public MqttSubscribeReply unsubscribeMqtt(MqttUnsubscribeRequest unsubscribeRequest) {
        int connectionId = unsubscribeRequest.getConnectionId().getConnectionId();
        logger.atInfo().log("UnsubscribeMqtt: unsubscribe on connectionId {}", connectionId);
        return blockingStub.unsubscribeMqtt(unsubscribeRequest);
    }

    /**
     * Publish MQTT message.
     *
     * @param publishRequest publish request
     * @return reply to publish
     * @throws StatusRuntimeException on errors
     */
    public MqttPublishReply publishMqtt(MqttPublishRequest publishRequest) {
        int connectionId = publishRequest.getConnectionId().getConnectionId();
        String topic = publishRequest.getMsg().getTopic();
        logger.atInfo().log("PublishMqtt: publishing on connectionId {} topic {}", connectionId, topic);
        return blockingStub.publishMqtt(publishRequest);
    }

    private void closeAllMqttConnections() {
        connections.forEach((connectionId, connectionControl) -> {
            if (connections.remove(connectionId, connectionControl)) {
                connectionControl.closeMqttConnection(DEFAULT_DISCONNECT_REASON);
            }
        });
    }

    private void disconnect() {
        if (! isDisconnected.getAndSet(true)) {
            if (! isShutdownSent.getAndSet(true)) {
                shutdownAgent("none");
            }
            channel.shutdown();
            try {
                channel.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
