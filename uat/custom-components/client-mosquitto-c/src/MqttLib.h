/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_MQTTLIB_H
#define MOSQUITTO_TEST_CLIENT_MQTTLIB_H

#include <string>
#include <mutex>
#include <unordered_map>

class MqttConnection;

/**
 * MQTT library class.
 */
class MqttLib {
public:
    MqttLib();
    ~MqttLib();

    /**
     * Create a MQTT connection.
     * @param client_id MQTT client id
     * @param host hostname of IP address of MQTT broker
     * @param port port of MQTT broker
     * @param keepalive keep alive interval
     * @param clean_session clean session flag
     * @param ca pointer to CA content, can be NULL
     * @param cert pointer to client's certificate content, can be NULL
     * @param key pointer to client's key content, can be NULL
     * @param v5 use MQTT v5.0
     * @return MqttConnection on success
     * @throw MqttException on errors
     */
    MqttConnection * createConnection(const std::string & client_id, const std::string & host, unsigned short port, unsigned short keepalive, bool clean_session, const char * ca, const char * cert, const char * key, bool v5);


    int registerConnection(MqttConnection * connection);
    MqttConnection * getConnection(int connection_id);
    MqttConnection * unregisterConnection(int connection_id);

private:
    std::mutex m_connections_mutex;

    std::unordered_map<int, MqttConnection *> m_connections;
    int m_connection_id_next;
};

#endif /* MOSQUITTO_TEST_CLIENT_MQTTLIB_H */
