/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include <grpcpp/health_check_service_interface.h>                      /* EnableDefaultHealthCheckService() */
#include <grpcpp/ext/proto_server_reflection_plugin.h>                  /* InitProtoReflectionServerBuilderPlugin() */

#include "GRPCLib.h"                    /* self header */
#include "GRPCLink.h"                   /* class GRPCLink */
#include "logger.h"                     /* logd() */


GRPCLib::GRPCLib() {
    logd("Initialize gRPC library\n");

    grpc::EnableDefaultHealthCheckService(true);
    grpc::reflection::InitProtoReflectionServerBuilderPlugin();
}

GRPCLib::~GRPCLib() {
    logd("Shutdown gRPC library\n");
}

GRPCLink * GRPCLib::makeLink(const std::string & agent_id, const std::list<std::string> & hosts, unsigned short port) {
    return new GRPCLink(agent_id, hosts, port);
}
