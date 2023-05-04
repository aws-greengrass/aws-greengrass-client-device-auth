/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_MQTTCONNECTION_H
#define MOSQUITTO_TEST_CLIENT_MQTTCONNECTION_H

#include <list>
#include <vector>
#include <unordered_map>
#include <mutex>

namespace ClientControl {
    class Mqtt5ConnAck;
}
class PendingRequest;

extern "C" struct mosquitto;
extern "C" typedef struct mqtt5__property mosquitto_property;

/**
 * MQTT library class.
 *
 * NOTE: thread unsafe !
 */
class MqttConnection {
public:

    /**
     * Creates a MQTT connection.
     *
     * @param client_id MQTT client id
     * @param host hostname of IP address of MQTT broker
     * @param port port of MQTT broker
     * @param keepalive keep alive interval
     * @param clean_session clean session flag
     * @param ca pointer to CA content, can be NULL
     * @param cert pointer to client's certificate content, can be NULL
     * @param key pointer to client's key content, can be NULL
     * @param v5 use MQTT v5.0
     * @throw MqttException on errors
     */
    MqttConnection(const std::string & client_id, const std::string & host, unsigned short port, unsigned short keepalive, bool clean_session, const char * ca, const char * cert, const char * key, bool v5);
    ~MqttConnection();

    /**
     * Starts a MQTT connection.
     *
     * @param timeout the timeout in seconds to connect
     * @return the pointer to gRPC representation of CONNACK
     * @throw MqttException on errors
     */
    ClientControl::Mqtt5ConnAck * start(unsigned timeout);

    /**
     * Subscribes on topics by filter.
     *
     * @param timeout the timeout in seconds to subscribe
     * @param subscription_id the optional subscription id
     * @param filters the filters of topics subscribe to
     * @param qos the common QoS value for all filters
     * @param retain_handling the common retain handling value for all filters
     * @param no_local the common no local value for all filters
     * @param retain_as_published the common retain as published vlaue for all filters
     * @return the vector of reason codes for each filter
     * @throw MqttException on errors
     */
    std::vector<int> subscribe(unsigned timeout, int * subscription_id, const std::list<std::string> & filters, int qos, int retain_handling, bool no_local, bool retain_as_published);

    /**
     * Disconnect from the broker.
     *
     * @param reason_code the MQTT disconnect reason code
     * @throw MqttException on errors
     */
    void disconnect(unsigned timeout, unsigned char reason_code);


private:
    PendingRequest * createPendingRequestLocked(int request_id);
    PendingRequest * getValidPendingRequestLocked(int request_id);
    void removePendingRequestUnlocked(int request_id);
    void removePendingRequestLocked(int request_id);

    static void on_connect(struct mosquitto *, void * obj, int reason_code, int flags, const mosquitto_property * props);
    void onConnect(int reason_code, int flags, const mosquitto_property * props);
    ClientControl::Mqtt5ConnAck * convertPropListToConnack(int reason_code, int flags, const mosquitto_property * proplist);

    static void on_disconnect(struct mosquitto *, void * obj, int rc, const mosquitto_property * props);
    void onDisconnect(int rc, const mosquitto_property * props);

    static void on_subscribe(struct mosquitto *, void * obj, int mid, int qos_count, const int * granted_qos, const mosquitto_property *props);
    void onSubscribe(int mid, int qos_count, const int * granted_qos, const mosquitto_property *props);

    static void removeFile(std::string & file);
    static std::string saveToTempFile(const std::string & content);

    void destroyLocked();
    void destroyUnlocked();
    void stateCheck();


    std::mutex m_mutex;
    std::string m_client_id;
    std::string m_host;
    unsigned short m_port;
    unsigned short m_keepalive;
    bool m_clean_session;
    bool m_v5;

    std::string m_ca;
    std::string m_cert;
    std::string m_key;

    struct mosquitto * m_mosq;

    std::string m_ca_file;
    std::string m_cert_file;
    std::string m_key_file;

    std::unordered_map<int, PendingRequest*> m_requests;
};

#endif /* MOSQUITTO_TEST_CLIENT_MQTTCONNECTION_H */
