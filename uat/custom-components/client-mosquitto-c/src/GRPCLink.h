/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_GRPCLINK_H
#define MOSQUITTO_TEST_CLIENT_GRPCLINK_H

#include <list>
#include <string>

class MqttLib;
class GRPCDiscoveryClient;
class GRPCControlServer;

/**
 * GRPCLink class.
 *
 * Represent bi-directinal gRPC communication channel with control.
 */
class GRPCLink {
public:
    /**
     * Establish connection(s) with the testing framework.
     *
     * @param agent_id id of agent to identify control channel by server
     * @param hosts host names/IPs to connect to testing framework
     * @param port TCP port to connect to
     * @return connection handler on success, NULL on errors
     */
    GRPCLink(const std::string & agent_id, const std::list<std::string> & hosts, unsigned short port);
    ~GRPCLink();

    /**
     * Handle gRPC requests.
     *
     * @param mqtt MQTT library handler
     * @return the reason of shutdown
     */
    std::string handleRequests(MqttLib & mqtt) const;

    /**
     * Unregister MQTT client control in testing framework.
     *
     * @param reason reason of shutdown
     */
    void shutdown(const std::string & reason);

    /**
     * Stop handling requests.
     *
     */
    void stopHandling() const;
private:
    void tryOneHost(const std::string & agent_id, const std::string & host, unsigned short port);

    GRPCDiscoveryClient * m_client;                     /** gRPC client */
    GRPCControlServer * m_server;                       /** gRPC server */
};

#endif /* MOSQUITTO_TEST_CLIENT_GRPCLINK_H */
