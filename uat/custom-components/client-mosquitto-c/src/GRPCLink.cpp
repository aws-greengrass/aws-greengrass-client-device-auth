/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include "GRPCLink.h"                     /* self header */
#include "GRPCDiscoveryClient.h"                /* class GRPCDiscoveryClient */
#include "GRPCControlServer.h"                  /* class GRPCControlServer */

#include "GRPCException.h"
#include "logger.h"                             /* logd() */

/** Value used to autoselect TCP port. */
#define AUTOSELECT_PORT                         0


GRPCLink::GRPCLink(const std::string & agent_id, const std::list<std::string> & hosts, unsigned short port) {
    GRPCException * last_exception;
    for (auto const & host : hosts) {
        try {
            tryOneHost(agent_id, host, port);
            logd("gPRC link established with %s:%hu as agent_id '%s'\n", host.c_str(), port, agent_id.c_str());
            return;
        } catch (GRPCException & ex) {
            last_exception = &ex;
        }
    }

    throw *last_exception;
}

GRPCLink::~GRPCLink() {
    shutdown("Program termination");
}


void GRPCLink::shutdown(const std::string & reason) {
    if (m_client && m_server) {
        logd("Shutdown gPRC link\n");
        m_client->UnregisterAgent(reason);

        delete m_server;
        m_server = 0;

        delete m_client;
        m_client = 0;
    }
}

std::string GRPCLink::handleRequests(MqttLib & mqtt) const {
    logd("Handle gRPC requests\n");

    if (m_server) {
        m_server->wait(mqtt);
        std::string reason = "Agent shutdown by OTF request '" + m_server->getShutdownReason() + "'";
        return reason;
    } else {
        throw GRPCException("Illegal state to handler requests");
    }
}

void GRPCLink::stopHandling() const {
    if (m_server) {
        m_server->unblockWait();
    }
}

void GRPCLink::tryOneHost(const std::string & agent_id, const std::string & host, unsigned short port) {
    GRPCDiscoveryClient * client = 0;
    GRPCControlServer * server = 0;

    logd("Making gPRC link with %s:%hu as agent_id '%s'\n", host.c_str(), port, agent_id.c_str());
    std::string otf_address = GRPCControlServer::buildAddress(host.c_str(), port);

    try {
        GRPCDiscoveryClient * client = new GRPCDiscoveryClient(agent_id, grpc::CreateChannel(otf_address, grpc::InsecureChannelCredentials()));
        if (client) {
            // get local address of the client and use it to create server
            std::string local_ip;
            if (client->RegisterAgent(local_ip)) {
                logd("Local address is %s\n", local_ip.c_str());

                GRPCControlServer * server = new GRPCControlServer(*client, local_ip.c_str(), AUTOSELECT_PORT);
                if (server) {
                    unsigned short my_service_port = server->getPort();
                    if (client->DiscoveryAgent(local_ip.c_str(), my_service_port)) {
                        m_client = client;
                        m_server = server;
                    } else {
                         throw GRPCException("Couldn't discover client");  // TODO: add strerror(errno)
                    }
                } else {
                    throw GRPCException("Couldn't create GRPCControlServer instance");
                }
            } else {
                throw GRPCException("Couldn't register client");     // TODO: add strerror(errno)
            }
        } else {
            throw new GRPCException("Couldn't create GRPCDiscoveryClient instance");
        }
    } catch (GRPCException & ex) {
        if (server) {
            delete server;
        }
        if (client) {
            delete client;
        }
        throw;
    }
}
