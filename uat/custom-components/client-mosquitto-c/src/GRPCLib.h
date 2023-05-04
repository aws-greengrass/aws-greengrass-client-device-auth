/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_GRPCLIB_H
#define MOSQUITTO_TEST_CLIENT_GRPCLIB_H

#include <list>
#include <string>

class GRPCLink;

class GRPCLib {
public:

    /**
     * Initialize gRPC library.
     *
     */
    GRPCLib();

    /**
     * Shutdown gRPC library.
     */
    ~GRPCLib();

    /**
     * Establish link with the testing framework.
     *
     * @param agent_id id of agent to identify control channel by server
     * @param hosts host names/IPs to connect to testing framework
     * @param port TCP port to connect to
     * @return connection handler on success, NULL on errors
     */
    GRPCLink * makeLink(const std::string & agent_id, const std::list<std::string> & hosts, unsigned short port);
};

#endif /* MOSQUITTO_TEST_CLIENT_GRPCLIB_H */
