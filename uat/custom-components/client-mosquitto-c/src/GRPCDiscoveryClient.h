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

class Status;

/**
 * Client of gRPC MqttAgentDiscovery service.
 */
class GRPCDiscoveryClient {
public:
    GRPCDiscoveryClient(const std::string & agent_id, std::shared_ptr<Channel> channel)
        : m_agent_id(agent_id), m_stub(MqttAgentDiscovery::NewStub(channel)) {
    }

    bool RegisterAgent(std::string & local_ip);
    bool DiscoveryAgent(const char * address, unsigned short port);
    bool UnregisterAgent(const std::string & reason);

private:
    bool checkStatus(const Status & status);


    std::string m_agent_id;
    std::unique_ptr<MqttAgentDiscovery::Stub> m_stub;
};

#endif /* MOSQUITTO_TEST_CLIENT_GRPCDISCOVERYCLIENT_H */
