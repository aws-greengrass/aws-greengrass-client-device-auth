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
    private static final DisconnectReasonCode DEFAULT_DISCONNECT_REASON
            = DisconnectReasonCode.NORMAL_DISCONNECTION;

    private static final Logger logger = LogManager.getLogger(AgentControlImpl.class);

    private final AtomicBoolean isShutdownSent = new AtomicBoolean(false);
    private final AtomicBoolean isDisconnected = new AtomicBoolean(false);
    private final ConcurrentHashMap<Integer, ConnectionControlImpl> connections = new ConcurrentHashMap<>();


    // That lock prevent cases when messages are received but connection not yet registered.
    private final Lock connectLock = new ReentrantLock();

    private final EngineControlImpl engineControl;
    private final String agentId;
    private final String address;
    private final int port;
    private int timeout;
    private final ConnectionControlFactory connectionControlFactory;

    private ManagedChannel channel;
    private MqttClientControlGrpc.MqttClientControlBlockingStub blockingStub;


    interface ConnectionControlFactory {
        ConnectionControlImpl newConnectionControl(MqttConnectReply connectReply,
                                                    @NonNull ConnectionEvents connectionEvents,
                                                    @NonNull AgentControlImpl agentControl);
    }

    /**
     * Creates instanse of AgentControlImpl.
     *
     * @param engineControl the back reference to whole engineControl
     * @param agentId the id of agent
     * @param address the address of gRPC server of agent (MQTT client)
     * @param port the port of gRPC server of agent (MQTT client)
     */
    public AgentControlImpl(@NonNull EngineControlImpl engineControl, @NonNull String agentId, @NonNull String address,
                                int port) {
        this(engineControl, agentId, address, port, new ConnectionControlFactory() {
            @Override
            public ConnectionControlImpl newConnectionControl(MqttConnectReply connectReply,
                                                                @NonNull ConnectionEvents connectionEvents,
                                                                @NonNull AgentControlImpl agentControl) {
                return new ConnectionControlImpl(connectReply, connectionEvents, agentControl);
            }
        });
    }

    private AgentControlImpl(@NonNull EngineControlImpl engineControl, @NonNull String agentId, @NonNull String address,
                                int port, @NonNull ConnectionControlFactory connectionControlFactory) {

        this(engineControl, agentId, address, port, connectionControlFactory, null, null);
    }

    /**
     * Creates instanse of AgentControlImpl for testing.
     *
     * @param engineControl the back reference to whole engineControl
     * @param agentId id of agent
     * @param address address of gRPC server of agent (MQTT client)
     * @param port port of gRPC server of agent (MQTT client)
     * @param channel the channel
     * @param blockingStub the blockingStub
     */
    AgentControlImpl(@NonNull EngineControlImpl engineControl,
                        @NonNull String agentId,
                        @NonNull String address,
                        int port,
                        @NonNull ConnectionControlFactory connectionControlFactory,
                        ManagedChannel channel,
                        MqttClientControlGrpc.MqttClientControlBlockingStub blockingStub) {
        super();
        this.engineControl = engineControl;
        this.agentId = agentId;
        this.address = address;
        this.port = port;
        this.timeout = engineControl.getTimeout();
        this.connectionControlFactory = connectionControlFactory;
        this.channel = channel;
        this.blockingStub = blockingStub;
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public void startAgent() {
        this.channel = Grpc.newChannelBuilderForAddress(address, port, InsecureChannelCredentials.create()).build();
        this.blockingStub = MqttClientControlGrpc.newBlockingStub(this.channel);
    }

    @Override
    public void stopAgent(boolean sendShutdown) {
        if (sendShutdown) {
            closeAllMqttConnections();
        }
        disconnect(sendShutdown);
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public ConnectionControl getConnectionControl(@NonNull String connectionName) {
        for (ConcurrentHashMap.Entry<Integer, ConnectionControlImpl> entry : connections.entrySet()) {
            ConnectionControlImpl connectionControl = entry.getValue();
            if (connectionName.equals(connectionControl.getConnectionName())) {
                return connectionControl;
            }
        }
        return null;
    }

    @Override
    public void shutdownAgent(@NonNull String reason) {
        ShutdownRequest request = ShutdownRequest.newBuilder().setReason(reason).build();
        blockingStub.shutdownAgent(request);
        isShutdownSent.set(true);
        logger.atInfo().log("shutdown request sent successfully");
    }

    @Override
    public ConnectionControl createMqttConnection(@NonNull MqttConnectRequest connectRequest,
                                                    @NonNull ConnectionEvents connectionEvents) {
        // create new connect request with timeout set
        MqttConnectRequest.Builder builder = MqttConnectRequest.newBuilder(connectRequest);
        builder.setTimeout(timeout);

        ConnectionControlImpl connectionControl;
        try {
            connectLock.lock();
            MqttConnectReply response = blockingStub.createMqttConnection(builder.build());
            if (response.getConnected()) {
                int connectionId = response.getConnectionId().getConnectionId();
                connectionControl = connectionControlFactory.newConnectionControl(response, connectionEvents, this);
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
     * Checks is agent receives request on that gRPC server address.
     *
     * @param the address address of gRPC server of agent (MQTT client)
     * @param the port port of gRPC server of agent (MQTT client)
     * @return true when address is the same
     */
    boolean isOnThatAddress(@NonNull String address, int port) {
        return this.port == port && this.address.equals(address);
    }

    /**
     * Do MQTT subscription(s).
     *
     * @param subscribeRequest subscribe request
     * @return reply to subscribe
     * @throws StatusRuntimeException on errors
     */
    MqttSubscribeReply subscribeMqtt(@NonNull MqttSubscribeRequest subscribeRequest) {
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
    MqttSubscribeReply unsubscribeMqtt(@NonNull MqttUnsubscribeRequest unsubscribeRequest) {
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
    MqttPublishReply publishMqtt(@NonNull MqttPublishRequest publishRequest) {
        int connectionId = publishRequest.getConnectionId().getConnectionId();
        String topic = publishRequest.getMsg().getTopic();
        logger.atInfo().log("PublishMqtt: publishing on connectionId {} topic {}", connectionId, topic);
        return blockingStub.publishMqtt(publishRequest);
    }

    /**
     * Close MQTT connection to the broker.
     *
     * @param closeRequest parameters of MQTT disconnect
     * @throws StatusRuntimeException on errors
     */
    void closeMqttConnection(@NonNull MqttCloseRequest closeRequest) {
        blockingStub.closeMqttConnection(closeRequest);
        int connectionId = closeRequest.getConnectionId().getConnectionId();
        connections.remove(connectionId);
        logger.atInfo().log("closeMqttConnection: MQTT connectionId {} closed", connectionId);
    }

    /**
     * Called when MQTT message has been received.
     *
     * @param connectionId id of connected where receives message
     * @param message the received MQTT message
     */
    void onMessageReceived(int connectionId, @NonNull Mqtt5Message message) {
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
    void onMqttDisconnect(int connectionId, @NonNull Mqtt5Disconnect disconnect, String error) {
        ConnectionControlImpl connectionControl = connections.get(connectionId);
        if (connectionControl == null) {
            logger.atWarn().log("MQTT disconnect received but connection with id {} could not found", connectionId);
        } else {
            // NOTE: connectionControl is not unregistered until closeMqttConnection() explicitly called
            connectionControl.onMqttDisconnect(disconnect, error);
        }
    }

    EngineControlImpl getEngineControl() {
        return engineControl;
    }

    private void closeAllMqttConnections() {
        connections.forEach((connectionId, connectionControl) -> {
            if (connections.remove(connectionId, connectionControl)) {
                connectionControl.closeMqttConnection(DEFAULT_DISCONNECT_REASON.getValue());
            }
        });
    }

    private void disconnect(boolean sendShutdown) {
        if (!isDisconnected.getAndSet(true)) {
            if (sendShutdown && !isShutdownSent.getAndSet(true)) {
                shutdownAgent("none");
            }
            channel.shutdown();
            try {
                channel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
