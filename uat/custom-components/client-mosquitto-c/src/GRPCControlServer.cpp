/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include <thread>
#include <sstream>

#include <grpcpp/grpcpp.h>
#include <google/protobuf/repeated_field.h>

#include "GRPCControlServer.h"                  /* self header */
#include "MqttLib.h"
#include "MqttConnection.h"
#include "MqttException.h"
#include "logger.h"                             /* logd() loge() */


#define PORT_MIN                        1
#define PORT_MAX                        65535

#define KEEPALIVE_OFF                   0
#define KEEPALIVE_MIN                   5
#define KEEPALIVE_MAX                   65535

#define TIMEOUT_MIN                     1

#define REASON_MIN                      0
#define REASON_MAX                      255

#define SIBSCRIPTION_ID_MIN             1
#define SIBSCRIPTION_ID_MAX             268435455

#define QOS_MIN                         0
#define QOS_MAX                         2

#define RETAIN_HANDLING_MIN             0
#define RETAIN_HANDLING_MAX             2

using google::protobuf::RepeatedPtrField;
using grpc::ServerBuilder;
using grpc::StatusCode;
using ClientControl::MqttConnectionId;
using ClientControl::MqttProtoVersion;
using ClientControl::TLSSettings;
using ClientControl::Mqtt5Message;
using ClientControl::MqttQoS;


std::string GRPCControlServer::buildAddress(const char * host, unsigned short port) {
    char server[201];
    int n = snprintf(server, sizeof(server), "%s:%hu", host, port);

    std::string address(server, n);
    return address;
}

GRPCControlServer::GRPCControlServer(GRPCDiscoveryClient & client, const char * host, unsigned short port) 
    : m_client(client), m_mqtt(0) {

    std::string address = buildAddress(host, port);

    ServerBuilder builder;
    builder.AddListeningPort(address, grpc::InsecureServerCredentials(), &m_choosen_port);
    builder.RegisterService(this);
    m_server = builder.BuildAndStart();
    logd("GRPCControlServer created and listed on %s:%hu\n", host, m_choosen_port);
}

void GRPCControlServer::wait(MqttLib & mqtt) {
    m_mqtt = &mqtt;

    auto serveFn = [&]() {
        m_server->Wait();
    };

    std::thread separate_thread(serveFn);
    auto f = m_exit_requested.get_future();
    f.wait();
    m_server->Shutdown();
    separate_thread.join();
}

Status GRPCControlServer::ShutdownAgent(ServerContext *, const ShutdownRequest * request, Empty *) {
    m_shutdown_reason = request->reason();
    logd("ShutdownAgent with reason '%s'\n", m_shutdown_reason.c_str());

    unblockWait();
    return Status::OK;
}

Status GRPCControlServer::CreateMqttConnection(ServerContext *, const MqttConnectRequest * request, MqttConnectReply * reply) {
    const std::string & client_id = request->clientid();
    const std::string & host = request->host();
    int port = request->port();
    logd("CreateMqttConnection client_id '%s' broker %s:%hu\n", client_id.c_str(), host.c_str(), port);

    if (client_id.empty()) {
        loge("CreateMqttConnection: clientId can't be empty\n");
        return Status(StatusCode::INVALID_ARGUMENT, "clientId can't be empty");
    }


    if (host.empty()) {
        loge("CreateMqttConnection: host can't be empty\n");
        return Status(StatusCode::INVALID_ARGUMENT, "host can't be empty");
    }

    if (port < PORT_MIN || port > PORT_MAX) {
        loge("CreateMqttConnection: invalid port, must be in range [%u, %u]\n", PORT_MIN, PORT_MAX);
        return Status(StatusCode::INVALID_ARGUMENT, "invalid port, must be in range [1, 65535]");
    }

    bool v5 = false;
    MqttProtoVersion version = request->protocolversion();
    if (version == MqttProtoVersion::MQTT_PROTOCOL_V_50) {
        v5 = true;
    } else {
        if (version != MqttProtoVersion::MQTT_PROTOCOL_V_311) {
            loge("CreateMqttConnection: MQTT_PROTOCOL_V_311 or MQTT_PROTOCOL_V_50 are only supported but %d requested\n", (int)version);
            return Status(StatusCode::INVALID_ARGUMENT, "invalid protocolVersion, only MQTT_PROTOCOL_V_311 and MQTT_PROTOCOL_V_50 are supported");
        }
    }

    int keepalive = request->keepalive();
    if (keepalive != KEEPALIVE_OFF && (keepalive < KEEPALIVE_MIN || keepalive > KEEPALIVE_MAX)) {
        loge("CreateMqttConnection: invalid keepalive, must be in range [%u, %u]\n", KEEPALIVE_MIN, KEEPALIVE_MAX);
        return Status(StatusCode::INVALID_ARGUMENT, "invalid keepalive, must be in range [5, 65535]");
    }

    int timeout = request->timeout();
    if (timeout < TIMEOUT_MIN) {
        loge("CreateMqttConnection: invalid timeout, must be at least %u second\n", TIMEOUT_MIN);
        return Status(StatusCode::INVALID_ARGUMENT, "invalid timeout, must be at least 1");
    }

    std::string ca;
    const char * ca_char = NULL;
    const char * cert_char = NULL;
    const char * key_char = NULL;
    if (request->has_tls()) {
        const TLSSettings & tls_settings = request->tls();
        ca = getJoinedCA(tls_settings);
        const std::string & cert = tls_settings.cert();
        const std::string & key = tls_settings.key();

        if (ca.empty()) {
            loge("CreateMqttConnection: ca is empty\n");
            return Status(StatusCode::INVALID_ARGUMENT, "CA list is empty");
        }

        if (cert.empty()) {
            loge("CreateMqttConnection: cert is empty\n");
            return Status(StatusCode::INVALID_ARGUMENT, "cert is empty");
        }

        if (key.empty()) {
            loge("CreateMqttConnection: key is empty\n");
            return Status(StatusCode::INVALID_ARGUMENT, "key is empty");
        }

        ca_char = ca.c_str();
        cert_char = cert.c_str();
        key_char = key.c_str();
    }

    try {
        MqttConnection * connection = m_mqtt->createConnection(m_client, client_id, host, port, keepalive, request->cleansession(), ca_char, cert_char, key_char, v5, request->properties());
        ClientControl::Mqtt5ConnAck * conn_ack = connection->start(timeout);
        int connection_id = m_mqtt->registerConnection(connection);

        MqttConnectionId * connection_id_obj = new MqttConnectionId();  // FIXME: memleak of connection_id_obj ?
        connection_id_obj->set_connectionid(connection_id);
        reply->set_allocated_connectionid(connection_id_obj);

        reply->set_connected(true);
        reply->set_allocated_connack(conn_ack);                         // FIXME: memleak of conn_ack ?

        return Status::OK;
    } catch (MqttException & ex) {
        loge("CreateMqttConnection: exception during connecting: %s\n", ex.getMessage().c_str());
        return Status(StatusCode::INTERNAL, ex.getMessage());
    }
}

Status GRPCControlServer::CloseMqttConnection(ServerContext *, const MqttCloseRequest * request, Empty *) {
    int timeout = request->timeout();
    if (timeout < TIMEOUT_MIN) {
        loge("CloseMqttConnection: invalid timeout, must be at least %u second\n", TIMEOUT_MIN);
        return Status(StatusCode::INVALID_ARGUMENT, "invalid timeout, must be at least 1");
    }

    int reason = request->reason();
    if (reason < REASON_MIN || reason > REASON_MAX) {
        loge("CreateMqttConnection: invalid disconnect reason %d\n", reason);
        return Status(StatusCode::INVALID_ARGUMENT, "invalid disconnect reason");
    }

    int connection_id = request->connectionid().connectionid();
    logd("CloseMqttConnection connection_id %d reason %d\n", connection_id, reason);

    MqttConnection * connection = m_mqtt->unregisterConnection(connection_id);
    if (!connection) {
        loge("CloseMqttConnection: connection with id %d doesn't found\n", connection_id);
        return Status(StatusCode::NOT_FOUND, "connection for that id doesn't found");
    }

    try {
        connection->disconnect(timeout, reason, &request->properties());
        delete connection;
        return Status::OK;
    } catch (MqttException & ex) {
        loge("CloseMqttConnection: exception during disconnecting\n");
        return Status(StatusCode::INTERNAL, ex.getMessage());
    }
}

Status GRPCControlServer::PublishMqtt(ServerContext *, const MqttPublishRequest * request, MqttPublishReply * reply) {
    if (!request->has_msg()) {
        loge("PublishMqtt: message is missing\n");
        return Status(StatusCode::INVALID_ARGUMENT, "message is missing");
    }

    const Mqtt5Message & message = request->msg();
    MqttQoS qos = message.qos();
    if (qos < QOS_MIN || qos > QOS_MAX) {
        loge("PublishMqtt: invalid QoS %d, must be in range [%d,%d]\n", (int)qos, QOS_MIN, QOS_MAX);
        return Status(StatusCode::INVALID_ARGUMENT, "invalid QoS, must be in range [0,2]");
    }

    const std::string & topic = message.topic();
    if (topic.empty()) {
        loge("PublishMqtt: topic is empty\n");
        return Status(StatusCode::INVALID_ARGUMENT, "topic is empty");
    }

    int timeout = request->timeout();
    if (timeout < TIMEOUT_MIN) {
        loge("PublishMqtt: invalid timeout, must be at least %u second\n", TIMEOUT_MIN);
        return Status(StatusCode::INVALID_ARGUMENT, "invalid publish timeout, must be >= 1");
    }


    if (!request->has_connectionid()) {
        loge("PublishMqtt: missing connection id\n");
        return Status(StatusCode::INVALID_ARGUMENT, "missing connectionId");
    }

    const MqttConnectionId & connection_id_obj = request->connectionid();
    int connection_id = connection_id_obj.connectionid();

    bool is_retain = message.retain();
    logd("PublishMqtt connection_id %d topic %s retain %d\n", connection_id, topic.c_str(), (int)is_retain);

    MqttConnection * connection = m_mqtt->getConnection(connection_id);
    if (!connection) {
        loge("PublishMqtt: connection with id %d doesn't found\n", connection_id);
        return Status(StatusCode::NOT_FOUND, "PublishMqtt: connection for that id doesn't found");
    }

    try {
        ClientControl::MqttPublishReply * result = connection->publish(timeout, qos, is_retain, topic, message.payload(), message.properties());
        if (result) {
            if (result->has_reasoncode()) {
                reply->set_reasoncode(result->reasoncode());
            }
            if (result->has_reasonstring()) {
                reply->set_reasonstring(result->reasonstring());
            }
            // TODO: copy also user properties
            delete result;
        }
        return Status::OK;
    } catch (MqttException & ex) {
        loge("PublishMqtt: exception during publishing\n");
        return Status(StatusCode::INTERNAL, ex.getMessage());
    }

    return Status::OK;
}

Status GRPCControlServer::SubscribeMqtt(ServerContext *, const MqttSubscribeRequest * request, MqttSubscribeReply * reply) {
    int timeout = request->timeout();
    if (timeout < TIMEOUT_MIN) {
        loge("SubscribeMqtt: invalid timeout, must be at least %u second\n", TIMEOUT_MIN);
        return Status(StatusCode::INVALID_ARGUMENT, "invalid timeout, must be at least 1");
    }

    int subscription_id;
    int * subscription_id_ptr = NULL;
    if (request->has_subscriptionid()) {
        subscription_id = request->subscriptionid();
        if (subscription_id < SIBSCRIPTION_ID_MIN || subscription_id > SIBSCRIPTION_ID_MAX) {
            loge("SubscribeMqtt: invalid subscription id %d must be >= %d and <= %d\n", subscription_id, SIBSCRIPTION_ID_MIN, SIBSCRIPTION_ID_MAX);
            return Status(StatusCode::INVALID_ARGUMENT, "invalid subscription id, must be >= 1 and <= 268435455");
        }
        subscription_id_ptr = &subscription_id;
    }

    std::vector<std::string> filters;
    int common_qos = 0;
    int common_retain_handling = 0;
    bool common_no_local = false;
    bool common_retain_as_published = false;
    int index = 0;
    for (auto const & subscription : request->subscriptions()) {
        const std::string filter = subscription.filter();
        if (filter.empty()) {
            loge("SubscribeMqtt: empty filter at subscription index %d\n", index);
            return Status(StatusCode::INVALID_ARGUMENT, "empty filter");
        }

        const int qos = subscription.qos();
        if (qos < QOS_MIN || qos > QOS_MAX) {
            loge("SubscribeMqtt: invalid QoS %d at subscription index %d, must be in range [{},{}]\n", qos, index, QOS_MIN, QOS_MAX);
            return Status(StatusCode::INVALID_ARGUMENT, "invalid QoS, must be in range [0,2]");
        }

        int retain_handling = subscription.retainhandling();
        if (retain_handling < RETAIN_HANDLING_MIN || retain_handling > RETAIN_HANDLING_MAX) {
            loge("SubscribeMqtt: invalid retainHandling %d at subscription index %d, must be in range [{},{}]\n", qos, index, RETAIN_HANDLING_MIN, RETAIN_HANDLING_MAX);
            return Status(StatusCode::INVALID_ARGUMENT, "invalid retainHandling, must be in range [0,2]");
        }

        bool no_local = subscription.nolocal();
        bool retain_as_published = subscription.retainaspublished();

        if (index == 0) {
            common_qos = qos;
            common_retain_handling = retain_handling;
            common_no_local = no_local;
            common_retain_as_published = retain_as_published;
        } else {
            if (qos != common_qos) {
                loge("SubscribeMqtt: QoS values mismatched %d and %d at index %d in subscriptions, all QoS values for subscriptions must be the same for mosquitto\n", qos, common_qos, index);
                return Status(StatusCode::INVALID_ARGUMENT, "QoS values mismatched");
            }

            if (retain_handling != common_retain_handling) {
                loge("SubscribeMqtt: retain handling values mismatched %d and %d at index %d in subscriptions, all retain handling values for subscriptions must be the same for mosquitto\n", retain_handling, common_retain_handling, index);
                return Status(StatusCode::INVALID_ARGUMENT, "retain handling values mismatched");
            }

            if (no_local != common_no_local) {
                loge("SubscribeMqtt: no local values mismatched %d and %d at index %d in subscriptions, all no local values for subscriptions must be the same for mosquitto\n", no_local, common_no_local, index);
                return Status(StatusCode::INVALID_ARGUMENT, "retain handling values mismatched");
            }

            if (retain_as_published != common_retain_as_published) {
                loge("SubscribeMqtt: retain as published values mismatched %d and %d at index %d in subscriptions, all retain as published values for subscriptions must be the same for mosquitto\n", retain_as_published, common_retain_as_published, index);
                return Status(StatusCode::INVALID_ARGUMENT, "retain as published values mismatched");
            }
        }

        logd("Subscription: filter %s QoS %d noLocal %d retainAsPublished %d retainHandling %d\n", filter.c_str(), qos, no_local, retain_as_published, retain_handling);
        filters.push_back(filter);

        index++;
    }

    int connection_id = request->connectionid().connectionid();
    logd("SubscribeMqtt connection_id %d\n", connection_id);

    MqttConnection * connection = m_mqtt->getConnection(connection_id);
    if (!connection) {
        loge("SubscribeMqtt: connection with id %d doesn't found\n", connection_id);
        return Status(StatusCode::NOT_FOUND, "connection for that id doesn't found");
    }

    try {
        std::vector<int> reason_codes = connection->subscribe(timeout, subscription_id_ptr, filters, common_qos, common_retain_handling, common_no_local, common_retain_as_published, request->properties());
        for (int reason_code : reason_codes) {
            logd("subscribe reason code %d\n", reason_code);
            reply->add_reasoncodes(reason_code);
        }

        return Status::OK;
    } catch (MqttException & ex) {
        loge("SubscribeMqtt: exception during subscribing\n");
        return Status(StatusCode::INTERNAL, ex.getMessage());
    }
}

Status GRPCControlServer::UnsubscribeMqtt(ServerContext *, const MqttUnsubscribeRequest * request, MqttSubscribeReply * reply) {
    int timeout = request->timeout();
    if (timeout < TIMEOUT_MIN) {
        loge("UnsubscribeMqtt: invalid timeout, must be at least %u second\n", TIMEOUT_MIN);
        return Status(StatusCode::INVALID_ARGUMENT, "invalid timeout, must be at least 1");
    }

    if (!request->has_connectionid()) {
        loge("UnsubscribeMqtt: missing connection id\n");
        return Status(StatusCode::INVALID_ARGUMENT, "missing connectionId");
    }

    if (request->filters_size() < 1) {
        loge("UnsubscribeMqtt: empty filters list\n");
        return Status(StatusCode::INVALID_ARGUMENT, "empty filters list");
    }

    // convert filters

    const MqttConnectionId & connection_id_obj = request->connectionid();
    int connection_id = connection_id_obj.connectionid();
    logd("UnsubscribeMqtt connection_id %d\n", connection_id);

    MqttConnection * connection = m_mqtt->getConnection(connection_id);
    if (!connection) {
        loge("UnsubscribeMqtt: connection with id %d doesn't found\n", connection_id);
        return Status(StatusCode::NOT_FOUND, "connection for that id doesn't found");
    }

    std::vector<std::string> filters(request->filters().begin(), request->filters().end());
    try {
        std::vector<int> reason_codes = connection->unsubscribe(timeout, filters, request->properties());
        for (int reason_code : reason_codes) {
            reply->add_reasoncodes(reason_code);
        }
        return Status::OK;
    } catch (MqttException & ex) {
        loge("UnsubscribeMqtt: exception during unsubscribing\n");
        return Status(StatusCode::INTERNAL, ex.getMessage());
    }
}


std::string GRPCControlServer::getJoinedCA(const TLSSettings & tls_settings) {
    const RepeatedPtrField<std::string> & ca_list = tls_settings.calist();

    std::string result;

    for (const std::string & ca : ca_list) {
        if (!result.empty()) {
            result.append("\n");
        }

        result.append(ca);

    }

    return result;
}
