/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include <stdlib.h>                             /* atoi */

#include <csignal>
#include <list>
#include <stdexcept>                            /* std::invalid_argument() */


#include "MqttLib.h"
#include "GRPCLib.h"
#include "GRPCLink.h"
#include "ClientException.h"

#include "logger.h"                             /* logd() */

#define DEFAULT_GRPC_SERVER_IP          "127.0.0.1"
#define DEFAULT_GRPC_SERVER_PORT        47619



static GRPCLink * glink;

static void handler(int signo) {
    (void)signo;
    if (glink) {
        glink->stopHandling();
    }
}

static void printUsage(const char * prog) {
    loge("Usage: %s agent_id [port [host ...]\n", prog);
}

static void parseArgs(int argc, char * argv[], std::string & agent_id, std::list<std::string> & addresses, unsigned short & port) {
    int intValue;
    int index;
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wimplicit-fallthrough"
    switch (argc) {
        default:
        case 4:
            // agent_id port ip ip ip
            addresses.clear();
            for (index = 3; index < argc; index++) {
                addresses.push_back(argv[index]);
            }
            // passthrough
        case 3:
            // agent_id port
            intValue = atoi(argv[2]);
            if (intValue < 1 || intValue > 65535) {
                throw std::invalid_argument("Invalid port value %s, expected [1..65535]\n"); //  loge("Invalid port value %s, expected [1..65535]\n", argv[2]);
            }
            port = intValue;
            // passthrough
        case 2:
            // agent_id
            agent_id = argv[1];
            break;
        case 1:
        case 0:
            // no arguments, missing agent_id, error
            throw std::invalid_argument("Invalid number of arguments, expected as least 1\n");
    }
#pragma GCC diagnostic pop
}

void doAll(int argc, char * argv[]) {
    // argument parsing
    std::string agent_id;
    std::list<std::string> addresses;
    addresses.push_back(DEFAULT_GRPC_SERVER_IP);
    unsigned short port = DEFAULT_GRPC_SERVER_PORT;

    parseArgs(argc, argv, agent_id, addresses, port);

    // processing
    GRPCLib gprcLib;
    GRPCLink * link = gprcLib.makeLink(agent_id, addresses, port);

    MqttLib mqttLib;

    glink = link;
    std::signal(SIGINT, handler);
    std::signal(SIGTERM, handler);
    std::signal(SIGQUIT, handler);

    const std::string & reason = link->handleRequests(mqttLib);

    glink = 0;

    link->shutdown(reason);
    delete link;
}

int main(int argc, char * argv[]) {
    try {
        doAll(argc, argv);
        logd("Execution done\n");
        return 0;
    } catch (std::invalid_argument & ex) {
        loge(ex.what());
        printUsage(argv[0]);
        return 1;
    } catch (ClientException & ex) {
        loge(ex.getMessage().c_str());                    // TODO: use error code
        return 2;
    }
    return 3;
}
