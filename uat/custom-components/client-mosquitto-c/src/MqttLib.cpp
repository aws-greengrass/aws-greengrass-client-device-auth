/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include <mosquitto.h>                          /* mosquitto_lib_init() mosquitto_lib_cleanup() mosquitto_lib_version() */

#include "MqttLib.h"                            /* self header */
#include "MqttConnection.h"
#include "MqttException.h"
#include "logger.h"                             /* logd() */

// references: mosquitto/client/pub_client.c

MqttLib::MqttLib()
    : m_connection_id_next(0) {
    logd("Initialize Mosquitto MQTT library\n");

    int major, minor, revision;
    mosquitto_lib_version(&major, &minor, &revision);
    logd("Mosquitto library version %d.%d.%d\n", major, minor, revision);

    int rc = mosquitto_lib_init();
    if (rc != MOSQ_ERR_SUCCESS) {
        throw MqttException("Couldn't initialize mosquitto library", rc);
    }
}

MqttLib::~MqttLib() {
    logd("Shutdown MQTT library\n");
}

MqttConnection * MqttLib::createConnection(GRPCDiscoveryClient & grpc_client, const std::string & client_id, const std::string & host, unsigned short port, unsigned short keepalive, bool clean_session, const char * ca, const char * cert, const char * key, bool v5) {
    return new MqttConnection(grpc_client, client_id, host, port, keepalive, clean_session, ca, cert, key, v5);
}


int MqttLib::registerConnection(MqttConnection * connection) {

    std::lock_guard<std::mutex> lk(m_connections_mutex);

    while (true) {
        int connection_id = ++m_connection_id_next;
        auto it = m_connections.find(connection_id);
        if (it == m_connections.end()) {
            m_connections.insert(std::make_pair(connection_id, connection));
            logd("Connection registered with id %d\n", connection_id);
            connection->setConnectionId(connection_id);
            return connection_id;
        } else {
            // test next connection_id;
        }
    }
}

MqttConnection * MqttLib::getConnection(int connection_id) {

    std::lock_guard<std::mutex> lk(m_connections_mutex);

    auto it = m_connections.find(connection_id);
    if (it == m_connections.end()) {
        return 0;
    }

    return it->second;
}

MqttConnection * MqttLib::unregisterConnection(int connection_id) {

    std::lock_guard<std::mutex> lk(m_connections_mutex);

    auto it = m_connections.find(connection_id);
    if (it == m_connections.end()) {
        return 0;
    }
    MqttConnection * connection = it->second;
    m_connections.erase(it);
    return connection;
}
