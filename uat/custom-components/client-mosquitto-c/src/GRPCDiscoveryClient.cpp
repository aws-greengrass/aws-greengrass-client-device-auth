/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include "GRPCDiscoveryClient.h"                /* self class */
#include "logger.h"                             /* logd() */

using grpc::ClientContext;

using ClientControl::Empty;
using ClientControl::RegisterReply;
using ClientControl::RegisterRequest;
using ClientControl::DiscoveryRequest;
using ClientControl::UnregisterRequest;
using ClientControl::OnReceiveMessageRequest;
using ClientControl::MqttConnectionId;
using ClientControl::OnMqttDisconnectRequest;



bool GRPCDiscoveryClient::RegisterAgent(std::string & local_ip) {
    RegisterRequest request;
    request.set_agentid(m_agent_id);

    RegisterReply reply;
    ClientContext context;

    logd("Sending RegisterAgent request with agent_id %s\n", m_agent_id.c_str());
    Status status = m_stub->RegisterAgent(&context, request, &reply);
    bool success = checkStatus(status);
    if (success) {
        local_ip = reply.address();
        return !local_ip.empty();                  // TODO: check IP is valid
    }

    return false;
}

bool GRPCDiscoveryClient::DiscoveryAgent(const char * address, unsigned short port) {
    DiscoveryRequest request;
    request.set_agentid(m_agent_id);
    request.set_address(address);
    request.set_port(port);

    Empty reply;
    ClientContext context;

    logd("Sending DiscoveryAgent request agent_id '%s' host:port %s:%hu\n", m_agent_id.c_str(), address, port);
    Status status = m_stub->DiscoveryAgent(&context, request, &reply);
    return checkStatus(status);
}


bool GRPCDiscoveryClient::UnregisterAgent(const std::string & reason) {
    UnregisterRequest request;
    request.set_agentid(m_agent_id);
    request.set_reason(reason);

    Empty reply;
    ClientContext context;

    logd("Sending UnregisterAgent request agent_id '%s' reason '%s'\n", m_agent_id.c_str(), reason.c_str());
    Status status = m_stub->UnregisterAgent(&context, request, &reply);
    return checkStatus(status);
}

bool GRPCDiscoveryClient::onReceiveMqttMessage(int connection_id, Mqtt5Message * message) {
    OnReceiveMessageRequest request;

    request.set_agentid(m_agent_id);
    MqttConnectionId * mqtt_connection_id = new MqttConnectionId();
    mqtt_connection_id->set_connectionid(connection_id);
    request.set_allocated_connectionid(mqtt_connection_id);

    request.set_allocated_msg(message);

    Empty reply;
    ClientContext context;

    // TODO: log also details of message
    logd("Sending OnReceiveMessage request agent_id '%s' connection_id %d\n", m_agent_id.c_str(), connection_id);
    Status status = m_stub->OnReceiveMessage(&context, request, &reply);
    return checkStatus(status);
}

bool GRPCDiscoveryClient::onMqttDisconnect(int connection_id, Mqtt5Disconnect * disconnect, const char * error) {
    OnMqttDisconnectRequest request;

    request.set_agentid(m_agent_id);
    MqttConnectionId * mqtt_connection_id = new MqttConnectionId();
    mqtt_connection_id->set_connectionid(connection_id);
    request.set_allocated_connectionid(mqtt_connection_id);

    request.set_allocated_disconnect(disconnect);
    if (error) {
        request.set_error(error);
    }

    Empty reply;
    ClientContext context;

    // TODO: also details of disconnect
    logd("Sending OnMqttDisconnect request agent_id '%s' connection_id %d error '%s'\n", m_agent_id.c_str(), connection_id, error);
    Status status = m_stub->OnMqttDisconnect(&context, request, &reply);
    return checkStatus(status);
}

bool GRPCDiscoveryClient::checkStatus(const Status & status) {
    bool ok = status.ok();
    if (!ok) {
        std::string message = status.error_message();
        std::string details = status.error_details();

        loge("gRPC request failed: %d: '%s': '%s'\n", (int)status.error_code(), message.c_str(), details.c_str());
    }

    return ok;
}
