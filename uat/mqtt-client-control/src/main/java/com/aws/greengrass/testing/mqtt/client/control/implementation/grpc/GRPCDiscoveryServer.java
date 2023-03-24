/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control.implementation.grpc;

import com.aws.greengrass.testing.mqtt.client.DiscoveryRequest;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.MqttAgentDiscoveryGrpc;
import com.aws.greengrass.testing.mqtt.client.OnMqttDisconnectRequest;
import com.aws.greengrass.testing.mqtt.client.OnReceiveMessageRequest;
import com.aws.greengrass.testing.mqtt.client.RegisterReply;
import com.aws.greengrass.testing.mqtt.client.RegisterRequest;
import com.aws.greengrass.testing.mqtt.client.UnregisterRequest;
import com.aws.greengrass.testing.mqtt.client.control.implementation.DiscoveryEvents;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GRPCDiscoveryServer extends MqttAgentDiscoveryGrpc.MqttAgentDiscoveryImplBase {
    private static final Logger logger = LogManager.getLogger(GRPCDiscoveryServer.class);

    private final DiscoveryEvents discoveryEvents;

    public GRPCDiscoveryServer(DiscoveryEvents discoveryEvents) {
        super();
        this.discoveryEvents = discoveryEvents;
    }

    @Override
    public void registerAgent(RegisterRequest request, StreamObserver<RegisterReply> responseObserver) {
        final String agentId = request.getAgentId();
        logger.atInfo().log("RegisterAgent: agentId {}", agentId);
        RegisterReply reply = RegisterReply.newBuilder().build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void discoveryAgent(DiscoveryRequest request, StreamObserver<Empty> responseObserver) {
        final String agentId = request.getAgentId();
        final String address = request.getAddress();
        final int port = request.getPort();
        logger.atInfo().log("DiscoveryClient: agentId {} address {} port {}", agentId, address, port);
        if (discoveryEvents != null) {
            discoveryEvents.onDiscoveryAgent(agentId, address, port);
        }

        Empty reply = Empty.newBuilder().build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void unregisterAgent(UnregisterRequest request, StreamObserver<Empty> responseObserver) {
        final String agentId = request.getAgentId();
        final String reason = request.getReason();
        logger.atInfo().log("UnregisterAgent: agentId {} reason {}", agentId, reason);

        if (discoveryEvents != null) {
            discoveryEvents.onUnregisterAgent(agentId);
        }

        Empty reply = Empty.newBuilder().build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }


    @Override
    public void onReceiveMessage(OnReceiveMessageRequest request, StreamObserver<Empty> responseObserver) {
        final String agentId = request.getAgentId();
        final int connectionId = request.getConnectionId().getConnectionId();
        final Mqtt5Message msg = request.getMsg();
        final String topic = msg.getTopic();
        final int qos = msg.getQos().getNumber();

        logger.atInfo().log("OnReceiveMessage: agentId {} connectionId {} topic {} QoS {}",
                                agentId, connectionId, topic, qos);

        if (discoveryEvents != null) {
            discoveryEvents.onMessageReceived(agentId, connectionId, msg);
        }

        Empty reply = Empty.newBuilder().build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void onMqttDisconnect(OnMqttDisconnectRequest request, StreamObserver<Empty> responseObserver) {
        final String agentId = request.getAgentId();
        final int connectionId = request.getConnectionId().getConnectionId();
        final Mqtt5Disconnect disconnect = request.getDisconnect();
        final String error = request.getError();
        logger.atInfo().log("OnMqttDisconnect: agentId {} connectionId {} disconnect '{}' error '{}'",
                            agentId, connectionId, disconnect, error);

        if (discoveryEvents != null) {
            discoveryEvents.onMqttDisconnect(agentId, connectionId, disconnect, error);
        }

        Empty reply = Empty.newBuilder().build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
