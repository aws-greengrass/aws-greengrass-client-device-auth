/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_GRPCCONTROLSERVER_H
#define MOSQUITTO_TEST_CLIENT_GRPCCONTROLSERVER_H

#include <future>

#include "mqtt_client_control.grpc.pb.h"                 /* autogenerated gRPC stuff */

using grpc::Server;
using grpc::ServerContext;
using grpc::Status;

namespace ClientControl {
    class Mqtt5ConnAck;
    class MqttPublishReply;
}

using ClientControl::Empty;
using ClientControl::MqttConnectReply;
using ClientControl::MqttConnectRequest;
using ClientControl::MqttCloseRequest;
using ClientControl::MqttClientControl;
using ClientControl::MqttSubscribeReply;
using ClientControl::MqttSubscribeRequest;
using ClientControl::MqttUnsubscribeRequest;
using ClientControl::MqttPublishReply;
using ClientControl::MqttPublishRequest;
using ClientControl::TLSSettings;
using ClientControl::ShutdownRequest;

class GRPCDiscoveryClient;
class MqttLib;
class MqttConnection;

/**
 * Server of gRPC MqttClientControl.
 */
class GRPCControlServer final : public MqttClientControl::Service {
public:
    GRPCControlServer(GRPCDiscoveryClient & client, const char * host, unsigned short port);

    /**
     * Get actual bound port.
     */
    unsigned short getPort() const { return m_choosen_port; }

    const std::string & getShutdownReason() const { return m_shutdown_reason; }
    /**
     * Handle incoming gRPC requests.
     * @return shutdown reason
     */
    void wait(MqttLib & mqtt);


    void unblockWait() { m_exit_requested.set_value(); }

    /**
     * Build gRPC address string from host and port.
     */
    static std::string buildAddress(const char * host, unsigned short port);

    Status ShutdownAgent(ServerContext * context, const ShutdownRequest * request, Empty * reply) override;
    Status CreateMqttConnection(ServerContext * context, const MqttConnectRequest * request, MqttConnectReply * reply) override;
    Status CloseMqttConnection(ServerContext * context, const MqttCloseRequest * request, Empty * reply) override;
    Status SubscribeMqtt(ServerContext * context, const MqttSubscribeRequest * request, MqttSubscribeReply * reply) override;
    Status UnsubscribeMqtt(ServerContext * context, const MqttUnsubscribeRequest * request, MqttSubscribeReply * reply) override;
    Status PublishMqtt(ServerContext * context, const MqttPublishRequest * request, MqttPublishReply * reply) override;


private:
    std::string getJoinedCA(const TLSSettings & tls_settings);

    int m_choosen_port;                 /** Bound port. */
    std::unique_ptr<Server> m_server;   /** Pointer to gRPC server. */

    GRPCDiscoveryClient & m_client;     /** Reference to gRPC client. */
    MqttLib * m_mqtt;                   /** Pointer to MQTT half. */
    std::string m_shutdown_reason;      /** String with shutdown reason as received by server from client. */
    std::promise<void> m_exit_requested;
};


#endif /* MOSQUITTO_TEST_CLIENT_GRPCCONTROLSERVER_H */