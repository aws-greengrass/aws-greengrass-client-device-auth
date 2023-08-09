/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_GRPCDISCOVERYCLIENT_H
#define MOSQUITTO_TEST_CLIENT_GRPCDISCOVERYCLIENT_H

#include <string>
#include <grpcpp/grpcpp.h>

#include "mqtt_client_control.grpc.pb.h"

using grpc::Channel;
using grpc::Status;
using ClientControl::MqttAgentDiscovery;
using ClientControl::Mqtt5Message;
using ClientControl::Mqtt5Disconnect;

class Status;

/**
 * Client of gRPC MqttAgentDiscovery service.
 */
class GRPCDiscoveryClient {
public:
    /**
     * Constructor of GRPCDiscoveryClient.
     *
     * @param agent_id the id of agent
     * @param channel gRPC communication channel
     */
    GRPCDiscoveryClient(const std::string & agent_id, std::shared_ptr<Channel> channel)
        : m_agent_id(agent_id), m_stub(MqttAgentDiscovery::NewStub(channel)) {
    }

    /**
     * Handles RegisterAgent gRPC request.
     *
     * @param local_ip the local IP of agent as seen by control
     */
    bool RegisterAgent(std::string & local_ip);

    /**
     * Handles DiscoveryAgent gRPC request.
     *
     * @param address the address of gRPC server of the control
     * @param port the port of gRPC server of the control
     */
    bool DiscoveryAgent(const char * address, unsigned short port);

    /**
     * Handles UnregisterAgent gRPC request.
     *
     * @param reason the reason of unregistration
     */
    bool UnregisterAgent(const std::string & reason);

    /**
     * Sends OnReceiveMqttMessage request to the control.
     *
     * @param connection_id the id MQTT connection
     * @param message the gRPC presentation of MQTT message, that function gets ownership of the pointer
     * @return true on success
     */
    bool onReceiveMqttMessage(int connection_id, Mqtt5Message * message);

    /**
     * Sends OnMqttDisconnect request to the control.
     *
     * @param connection_id the id MQTT connection
     * @param disconnect the gRPC presentation of DISCONNECT package info, that function gets ownership of the pointer
     * @param error the optional OS error string
     * @return true on success
     */
    bool onMqttDisconnect(int connection_id, Mqtt5Disconnect * disconnect, const char * error);

private:
    bool checkStatus(const Status & status);

    std::string m_agent_id;
    std::unique_ptr<MqttAgentDiscovery::Stub> m_stub;
};

#endif /* MOSQUITTO_TEST_CLIENT_GRPCDISCOVERYCLIENT_H */
