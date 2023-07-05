/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_MQTTLIB_H
#define MOSQUITTO_TEST_CLIENT_MQTTLIB_H

#include <string>
#include <mutex>
#include <unordered_map>
#include <google/protobuf/repeated_field.h>

using google::protobuf::RepeatedPtrField;

namespace ClientControl {
    class Mqtt5Properties;
}

class MqttConnection;
class GRPCDiscoveryClient;


/**
 * MQTT library class.
 */
class MqttLib {
public:
    MqttLib();
    ~MqttLib();

    /**
     * Creates a MQTT connection.
     *
     * @param grpc_client the reference to gRPC client
     * @param client_id MQTT client id
     * @param host hostname of IP address of MQTT broker
     * @param port port of MQTT broker
     * @param keepalive keep alive interval
     * @param clean_session clean session flag
     * @param ca pointer to CA content, can be NULL
     * @param cert pointer to client's certificate content, can be NULL
     * @param key pointer to client's key content, can be NULL
     * @param v5 use MQTT v5.0
     * @param user_properties the user properties of the connect request
     * @param request_response_information pointer to optional request response information flag
     * @return MqttConnection on success
     * @throw MqttException on errors
     */
    MqttConnection * createConnection(GRPCDiscoveryClient & grpc_client, const std::string & client_id,
                                        const std::string & host, unsigned short port, unsigned short keepalive,
                                        bool clean_session, const char * ca, const char * cert, const char * key,
                                        bool v5, const RepeatedPtrField<ClientControl::Mqtt5Properties> & user_properties,
                                        const bool * request_response_information);


    int registerConnection(MqttConnection * connection);
    MqttConnection * getConnection(int connection_id);
    MqttConnection * unregisterConnection(int connection_id);

private:
    std::mutex m_connections_mutex;

    std::unordered_map<int, MqttConnection *> m_connections;
    int m_connection_id_next;
};

#endif /* MOSQUITTO_TEST_CLIENT_MQTTLIB_H */
