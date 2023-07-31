/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef MOSQUITTO_TEST_CLIENT_MQTTCONNECTION_H
#define MOSQUITTO_TEST_CLIENT_MQTTCONNECTION_H

#include <vector>
#include <unordered_map>
#include <mutex>
#include <atomic>
#include <google/protobuf/repeated_field.h>

using google::protobuf::RepeatedPtrField;

namespace ClientControl {
    class Mqtt5ConnAck;
    class Mqtt5Disconnect;
    class Mqtt5Message;
    class MqttPublishReply;
    class Mqtt5Properties;
    class MqttSubscribeReply;
}
class PendingRequest;
class GRPCDiscoveryClient;

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
     * @param user_properties the user properties of the CONNECT request
     * @param request_response_information pointer to optional request response information flag
     * @throw MqttException on errors
     */
    MqttConnection(GRPCDiscoveryClient & grpc_client, const std::string & client_id, const std::string & host,
                    unsigned short port, unsigned short keepalive, bool clean_session, const char * ca,
                    const char * cert, const char * key, bool v5,
                    const RepeatedPtrField<ClientControl::Mqtt5Properties> & user_properties,
                    const bool * request_response_information);
    ~MqttConnection();


    void setConnectionId(int connection_id) {
        m_connection_id = connection_id;
    }

    /**
     * Starts a MQTT connection.
     *
     * @param timeout the timeout in seconds to connect
     * @return the pointer to allocated gRPC representation of CONNACK
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
     * @param user_properties the user properties of the SUBCRIBE request
     * @param reply the subscribe reply to update
     * @throw MqttException on errors
     */
    void subscribe(unsigned timeout, const int * subscription_id, const std::vector<std::string> & filters,
                    int qos, int retain_handling, bool no_local, bool retain_as_published,
                    const RepeatedPtrField<ClientControl::Mqtt5Properties> & user_properties,
                    ClientControl::MqttSubscribeReply * reply);


    /**
     * Unsubscribes from filters.
     *
     * @param timeout the timeout in seconds to subscribe
     * @param filters the filters of topics subscribe to
     * @param user_properties the user properties of the UNSUBCRIBE request
     * @param reply the subscribe reply to update
     * @throw MqttException on errors
     */
    void unsubscribe(unsigned timeout, const std::vector<std::string> & filters,
                        const RepeatedPtrField<ClientControl::Mqtt5Properties> & user_properties,
                        ClientControl::MqttSubscribeReply * reply);

    /**
     * Publishes MQTT message.
     *
     * @param timeout the timeout in seconds to publish
     * @param qos the QoS value
     * @param is_retain the retain flag
     * @param topic the topic to publish
     * @param payload the payload of the message
     * @param user_properties the user properties of the PUBLISH request
     * @param content_type the optional content type
     * @param payload_format_indicator the pointer to optional value of 'payload format indicator' of the message
     * @param message_expiry_interval the pointer to optinal value of 'message expiry interval'
     * @param response_topic the pointer to optional response topic
     * @param correlation_data the pointer to optional binary correlation data
     * @return pointer to allocated gRPC MqttPublishReply
     * @throw MqttException on errors
     */
    ClientControl::MqttPublishReply * publish(unsigned timeout, int qos, bool is_retain, const std::string & topic,
                                                const std::string & payload,
                                                const RepeatedPtrField<ClientControl::Mqtt5Properties> & user_properties,
                                                const std::string * content_type, const bool * payload_format_indicator,
                                                const int * message_expiry_interval, const std::string * response_topic,
                                                const std::string * correlation_data);

    /**
     * Disconnect from the broker.
     *
     * @param timeout the timeout in seconds to disconnect
     * @param reason_code the MQTT disconnect reason code
     * @param user_properties the optional user properties of the DISCONNECT request
     * @throw MqttException on errors
     */
    void disconnect(unsigned timeout, unsigned char reason_code,
                    const RepeatedPtrField<ClientControl::Mqtt5Properties> * user_properties);


private:
    PendingRequest * createPendingRequestLocked(int request_id);
    PendingRequest * getValidPendingRequestLocked(int request_id);
    void removePendingRequestUnlocked(int request_id);
    void removePendingRequestLocked(int request_id);

    static void on_connect(struct mosquitto *, void * obj, int reason_code, int flags, const mosquitto_property * props);
    void onConnect(int reason_code, int flags, const mosquitto_property * props);
    ClientControl::Mqtt5ConnAck * convertToConnack(int reason_code, int flags, const mosquitto_property * proplist);

    static void on_disconnect(struct mosquitto *, void * obj, int rc, const mosquitto_property * props);
    void onDisconnect(int rc, const mosquitto_property * props);
    ClientControl::Mqtt5Disconnect * convertToDisconnect(int reason_code, const mosquitto_property * props);


    static void on_subscribe(struct mosquitto *, void * obj, int mid, int qos_count, const int * granted_qos, const mosquitto_property *props);
    void onSubscribe(int mid, int qos_count, const int * granted_qos, const mosquitto_property *props);

    static void on_publish(struct mosquitto *, void * obj, int mid, int rc, const mosquitto_property * props);
    void onPublish(int mid, int rc, const mosquitto_property * props);
    ClientControl::MqttPublishReply * convertToPublishReply(int reason_code, const mosquitto_property * proplist);

    static void on_message(struct mosquitto *, void * obj, const struct mosquitto_message * message, const mosquitto_property * props);
    void onMessage(const struct mosquitto_message * message, const mosquitto_property * props);
    ClientControl::Mqtt5Message * convertToMqtt5Message(const struct mosquitto_message * message, const mosquitto_property * props);

    static void on_unsubscribe(struct mosquitto *, void * obj, int mid, const mosquitto_property * props);
    void onUnsubscribe(int mid, const mosquitto_property * props);

    static void on_log(struct mosquitto *, void *, int level, const char * str);

    static void removeFile(std::string & file);
    static std::string saveToTempFile(const std::string & content);

    void convertUserProperties(const RepeatedPtrField<ClientControl::Mqtt5Properties> * user_properties, mosquitto_property ** conn_properties);
    void updateMqttSubscribeReply(const std::vector<int> & granted_qos, const mosquitto_property * props,  ClientControl::MqttSubscribeReply * reply);

    void destroyLocked();
    void destroyUnlocked();
    void stateCheck();


    const int RECONNECT_DELAY_SEC = 86400; //one day

    std::mutex m_mutex;
    GRPCDiscoveryClient & m_grpc_client;
    std::string m_client_id;
    std::string m_host;
    unsigned short m_port;
    unsigned short m_keepalive;
    bool m_clean_session;
    bool m_v5;
    int m_connection_id;
    std::atomic_bool m_is_closing;
    std::atomic_bool m_is_connected;

    std::string m_ca;
    std::string m_cert;
    std::string m_key;

    mosquitto_property * m_conn_properties;
    bool * m_request_response_information;

    struct mosquitto * m_mosq;

    std::string m_ca_file;
    std::string m_cert_file;
    std::string m_key_file;

    std::unordered_map<int, PendingRequest*> m_requests;
};

#endif /* MOSQUITTO_TEST_CLIENT_MQTTCONNECTION_H */
