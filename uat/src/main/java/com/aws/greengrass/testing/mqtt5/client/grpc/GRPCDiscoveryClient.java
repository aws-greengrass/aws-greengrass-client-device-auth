/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt.client.DiscoveryReply;
import com.aws.greengrass.testing.mqtt.client.DiscoveryRequest;
import com.aws.greengrass.testing.mqtt.client.MqttAgentDiscoveryGrpc;
import com.aws.greengrass.testing.mqtt.client.RegisterRequest;
import com.aws.greengrass.testing.mqtt.client.UnregisterRequest;
import com.aws.greengrass.testing.mqtt5.client.exceptions.GRPCException;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of gRPC client used to discover agent.
 */
class GRPCDiscoveryClient {
    private static final Logger logger = LogManager.getLogger(GRPCDiscoveryClient.class);


    private final String agentId;
    private final MqttAgentDiscoveryGrpc.MqttAgentDiscoveryBlockingStub blockingStub;

    /**
     * Create instance of gRPC client for agent discovery.
     *
     * @param agentId id of agent to identify control channel by gRPC server
     * @param address address of gRPC service
     * @throws GRPCException on errors
     */
    GRPCDiscoveryClient(String agentId, String address) throws GRPCException {
        super();
        this.agentId = agentId;
        ManagedChannel channel = Grpc.newChannelBuilder(address, InsecureChannelCredentials.create()).build();
        this.blockingStub = MqttAgentDiscoveryGrpc.newBlockingStub(channel);
    }

    /**
     * Register the agent.
     *
     * @return IP address of client as visible by server
     * @throws GRPCException on errors
     */
    String registerAgent() throws GRPCException {
        RegisterRequest request = RegisterRequest.newBuilder()
                                        .setAgentId(agentId)
                                        .build();
        DiscoveryReply reply;

        try {
            reply = blockingStub.registerAgent(request);
        } catch (StatusRuntimeException ex) {
            logger.atError().withThrowable(ex).log("gRPC request failed");
            throw new GRPCException(ex);
        }
        return reply.getAddress();
    }

    /**
     * Discover the agent.
     *
     * @param address host of local gRPC service
     * @param port port of local gRPC service
     */
    void discoveryAgent(String address, int port) throws GRPCException {
        DiscoveryRequest request = DiscoveryRequest.newBuilder()
                                        .setAgentId(agentId)
                                        .setAddress(address)
                                        .setPort(port)
                                        .build();
        try {
            blockingStub.discoveryAgent(request);
        } catch (StatusRuntimeException ex) {
            logger.atError().withThrowable(ex).log("gRPC request failed");
            throw new GRPCException(ex);
        }
    }

    /**
     * Unregister agent.
     *
     * @param reason reason of unregistering
     * @throws GRPCException on errors
     */
    void unregisterAgent(String reason) throws GRPCException {
        UnregisterRequest request = UnregisterRequest.newBuilder()
                                        .setAgentId(agentId)
                                        .setReason(reason)
                                        .build();
        try {
            blockingStub.unregisterAgent(request);
        } catch (StatusRuntimeException ex) {
            logger.atError().withThrowable(ex).log("gRPC request failed");
            throw new GRPCException(ex);
        }
    }

    void close() {
        // TODO: implement
    }
}
