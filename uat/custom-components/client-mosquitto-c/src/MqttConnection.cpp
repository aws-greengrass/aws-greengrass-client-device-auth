/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

#include <cstdio>
#include <string>
#include <sstream>
#include <iterator>
#include <memory>
#include <future>


#include <mosquitto.h>                          /* mosquitto_new() mosquitto_destroy() mosquitto_connect_bind_v5() ... */
#include <mqtt_protocol.h>                      /* MQTT_RC_DISCONNECT_WITH_WILL_MSG */

#include "mqtt_client_control.grpc.pb.h"

#include "MqttConnection.h"                     /* self header */
#include "GRPCDiscoveryClient.h"                /* class GRPCDiscoveryClient */
#include "MqttException.h"
#include "logger.h"                             /* logd() */

#define DEFAULT_DISCONNECT_REASON       MQTT_RC_NORMAL_DISCONNECTION
#define DEFAULT_DISCONNECT_TIMEOUT      10

#define CONNECT_REQUEST_ID               (1024*64 + 1)
#define DISCONNECT_REQUEST_ID            (1024*64 + 2)

class AsyncResult {
public:
    int rc;                                     // all
    int flags;                                  // connect
    mosquitto_property * props;                 // all
    int mid;                                    // subscribe / publish / unsubscribe
    std::vector<int> granted_qos;               // subscribe

    // connect / disconnect
    AsyncResult(int rc_, int flags_, const mosquitto_property * props_)
        : rc(rc_), flags(flags_), props(NULL), mid(0) {
        mosquitto_property_copy_all(&props, props_);
    }

    // publish / unsubscribe
    AsyncResult(int mid_, const mosquitto_property * props_)
        : rc(MOSQ_ERR_SUCCESS), flags(0), props(0), mid(mid_) {
        mosquitto_property_copy_all(&props, props_);
    }

    // subscribe
    AsyncResult(int mid_, int qos_count_, const int * granted_qos_, const mosquitto_property * props_)
        : rc(MOSQ_ERR_SUCCESS), flags(0), props(0), mid(mid_), granted_qos(granted_qos_, granted_qos_ + qos_count_) {
        mosquitto_property_copy_all(&props, props_);
    }
    AsyncResult(const AsyncResult &) = delete;
    AsyncResult & operator=(const AsyncResult &) = delete;

    ~AsyncResult() {
        if (props) {
            mosquitto_property_free_all(&props);
        }
    }
};

class PendingRequest {
public:
    PendingRequest() : m_valid(true) {}

    std::shared_ptr<AsyncResult> waitForResult(unsigned timeout) {
        std::chrono::seconds duration(timeout);
        auto future = m_promise.get_future();
        std::future_status status = future.wait_for(duration);
        switch (status) {
            case std::future_status::deferred:
                throw MqttException("Operation deferred", -1);
                break;
            case std::future_status::timeout:
                throw MqttException("Operation timedout", -1);
                break;
            case std::future_status::ready:
                return future.get();
            default:
                throw MqttException("Operation failed", -1);
                break;
        }
    }

    void submitResult(const std::shared_ptr<AsyncResult> & result) {
        m_promise.set_value(result);
        m_valid = false;
    }

    bool isValid() const {
        return m_valid;
    }

private:
    std::promise<std::shared_ptr<AsyncResult> > m_promise;
    bool m_valid;
};


MqttConnection::MqttConnection(GRPCDiscoveryClient & grpc_client, const std::string & client_id, const std::string & host, unsigned short port, unsigned short keepalive, bool clean_session, const char * ca, const char * cert, const char * key, bool v5)
    : m_mutex(), m_grpc_client(grpc_client), m_client_id(client_id), m_host(host), m_port(port), m_keepalive(keepalive), m_clean_session(clean_session), m_v5(v5), m_connection_id(0), m_mosq(0), m_requests() {

    logd("Creating Mosquitto MQTT connection for %s:%hu\n", m_host.c_str(), m_port);

    if (ca) {
        m_ca = ca;
    }

    if (cert) {
        m_cert = cert;
    }

    if (key) {
        m_key = key;
    }
}

ClientControl::Mqtt5ConnAck * MqttConnection::start(unsigned timeout) {
    int rc;
    PendingRequest * request = 0;

    logd("Establishing Mosquitto MQTT connection to %s:%hu in %d seconds\n", m_host.c_str(), m_port, timeout);

    {
        std::lock_guard<std::mutex> lk(m_mutex);

        m_mosq = mosquitto_new(m_client_id.c_str(), m_clean_session, this);
        if (!m_mosq) {
            throw MqttException("couldn't initialize new connection instance", errno);
        }

        if (m_v5) {
            rc = mosquitto_int_option(m_mosq, MOSQ_OPT_PROTOCOL_VERSION, MQTT_PROTOCOL_V5);
            if (rc != MOSQ_ERR_SUCCESS) {
                loge("mosquitto_int_option failed with code %d: %s\n", rc, mosquitto_strerror(rc));
                destroyLocked();
                throw MqttException("could'n set protocol version to 5.0", rc);
            }
        }

        mosquitto_connect_v5_callback_set(m_mosq, on_connect);
        mosquitto_disconnect_v5_callback_set(m_mosq, on_disconnect);

        mosquitto_publish_v5_callback_set(m_mosq, on_publish);
        mosquitto_message_v5_callback_set(m_mosq, on_message);

        mosquitto_subscribe_v5_callback_set(m_mosq, on_subscribe);
        mosquitto_unsubscribe_v5_callback_set(m_mosq, on_unsubscribe);

        mosquitto_log_callback_set(m_mosq, on_log);

        // TODO: mosquitto_reconnect_delay_set(m_mosq, ...)


        if (!m_ca.empty() && !m_cert.empty() && !m_key.empty()) {
            logd("Use provided TLS credentials\n");
            m_ca_file = saveToTempFile(m_ca);
            m_cert_file = saveToTempFile(m_cert);
            m_key_file = saveToTempFile(m_key);

            rc = mosquitto_tls_set(m_mosq, m_ca_file.c_str(), NULL, m_cert_file.c_str(), m_key_file.c_str(), NULL);
            if (rc != MOSQ_ERR_SUCCESS) {
                loge("mosquitto_tls_set failed with code %d: %s\n", rc, mosquitto_strerror(rc));
                destroyLocked();
                throw MqttException("couldn't set TLS credentials", rc);
            }
            // TODO: select TLS version with mosquitto_tls_opts_set();
            // NOTE: enable for tests only
            // mosquitto_tls_insecure_set(m_mosq, true);
        }

        // TODO
        // mosquitto_will_set_v5()

        rc = mosquitto_loop_start(m_mosq);
        if (rc != MOSQ_ERR_SUCCESS) {
            loge("mosquitto_loop_start failed with code %d: %s\n", rc, mosquitto_strerror(rc));
            destroyLocked();
            throw MqttException("couldn't start interval library thread", rc);
        }


        // TODO: pass CONNECT properties
        // FIXME: there is no async v5 connect() !!!
        // rc = mosquitto_connect_bind_v5(m_mosq, m_host.c_str(), m_port, m_keepalive, NULL, NULL);
        rc = mosquitto_connect_bind_async(m_mosq, m_host.c_str(), m_port, m_keepalive, NULL);
        if (rc != MOSQ_ERR_SUCCESS) {
            loge("mosquitto_connect_bind_async failed with code %d: %s\n", rc, mosquitto_strerror(rc));
            destroyLocked();
            throw MqttException("couldn't establish MQTT connection", rc);
        }
        request = createPendingRequestLocked(CONNECT_REQUEST_ID);
    }


    std::shared_ptr<AsyncResult> result = request->waitForResult(timeout);

    removePendingRequestUnlocked(CONNECT_REQUEST_ID);

    ClientControl::Mqtt5ConnAck * conn_ack = convertToConnack(result->rc, result->flags, result->props);

    logd("Establishing MQTT connection to %s:%hu completed with reason code %d\n" , m_host.c_str(), m_port, result->rc);
    return conn_ack;
}

void MqttConnection::on_connect(struct mosquitto *, void * obj, int reason_code, int flags, const mosquitto_property * props) {
    ((MqttConnection*)obj)->onConnect(reason_code, flags, props);
}

void MqttConnection::onConnect(int reason_code, int flags, const mosquitto_property * props) {
    logd("onConnect rc %d flags %d props %p\n", reason_code, flags, props);

    std::lock_guard<std::mutex> lk(m_mutex);

    PendingRequest * request = getValidPendingRequestLocked(CONNECT_REQUEST_ID);
    if (request) {
        std::shared_ptr<AsyncResult> result(new AsyncResult(reason_code, flags, props));
        request->submitResult(result);
    }
}

MqttConnection::~MqttConnection() {
    logd("Destroy Mosquitto MQTT connection\n");
    disconnect(DEFAULT_DISCONNECT_TIMEOUT, DEFAULT_DISCONNECT_REASON);
}


void MqttConnection::disconnect(unsigned timeout, unsigned char reason_code) {

    PendingRequest * request = 0;
    {
        std::lock_guard<std::mutex> lk(m_mutex);

        if (m_mosq) {
            logd("Disconnect Mosquitto MQTT connection with reason code %d\n", (int)reason_code);

            // TODO: pass DISCONNECT properties
            // FIXME: there isn't async disconnect()
            int rc = mosquitto_disconnect_v5(m_mosq, reason_code, NULL);
            if (rc != MOSQ_ERR_SUCCESS) {
                throw MqttException("couldn't disconnect from MQTT broker", rc);
            }
            request = createPendingRequestLocked(DISCONNECT_REQUEST_ID);
        }
    }

    if (request) {
        std::shared_ptr<AsyncResult> result = request->waitForResult(timeout);

        removePendingRequestUnlocked(DISCONNECT_REQUEST_ID);
        destroyUnlocked();

        int rc = result->rc;
        if (rc != MOSQ_ERR_SUCCESS) {
            throw MqttException("couldn't disconnect from MQTT broker", rc);
        }
    }
}

void MqttConnection::on_disconnect(struct mosquitto *, void * obj, int rc, const mosquitto_property * props) {
    ((MqttConnection*)obj)->onDisconnect(rc, props);
}

void MqttConnection::onDisconnect(int rc, const mosquitto_property * props) {
    logd("onDisconnect rc %d props %p\n", rc, props);

    ClientControl::Mqtt5Disconnect * disconnect = convertToDisconnect(rc, props);
    m_grpc_client.onMqttDisconnect(m_connection_id, disconnect, NULL);

    std::lock_guard<std::mutex> lk(m_mutex);

    PendingRequest * request = getValidPendingRequestLocked(DISCONNECT_REQUEST_ID);
    if (request) {
        std::shared_ptr<AsyncResult> result(new AsyncResult(rc, 0, props));
        request->submitResult(result);
    }
}

std::vector<int> MqttConnection::subscribe(unsigned timeout, const int * subscription_id, const std::vector<std::string> & filters, int qos, int retain_handling, bool no_local, bool retain_as_published) {

    PendingRequest * request = 0;
    mosquitto_property * properties = NULL;
    int message_id = 0;

    std::vector<const char*> pointers;
    for (const std::string & filter : filters) {
        pointers.push_back(filter.c_str());
    }

    {
        std::lock_guard<std::mutex> lk(m_mutex);
        stateCheck();

        int options = 0;
        if (no_local) {
            options |= MQTT_SUB_OPT_NO_LOCAL;
        }

        if (retain_as_published) {
            options |= MQTT_SUB_OPT_RETAIN_AS_PUBLISHED;
        }

        if (retain_handling == 0) {
            options |= MQTT_SUB_OPT_SEND_RETAIN_ALWAYS;
        } else if (retain_handling == 1) {
            options |= MQTT_SUB_OPT_SEND_RETAIN_NEW;
        } else if (retain_handling == 2) {
            options |= MQTT_SUB_OPT_SEND_RETAIN_NEVER;
        }

        int rc;
        if (subscription_id) {
            rc = mosquitto_property_add_varint(&properties, MQTT_PROP_SUBSCRIPTION_IDENTIFIER, *subscription_id);
            if (rc != MOSQ_ERR_SUCCESS) {
                loge("mosquitto_property_add_varint failed with code %d: %s\n", rc, mosquitto_strerror(rc));
                throw MqttException("couldn't set subscription id", rc);
            }
        }

        // TODO: pass also user properties
        rc = mosquitto_subscribe_multiple(m_mosq, &message_id, pointers.size(), const_cast<char* const*>(pointers.data()), qos, options, properties);
        if (rc != MOSQ_ERR_SUCCESS) {
            loge("mosquitto_subscribe_multiple failed with code %d: %s\n", rc, mosquitto_strerror(rc));
            mosquitto_property_free_all(&properties);
            throw MqttException("couldn't subscribe", rc);
        }

        request = createPendingRequestLocked(message_id);
    }

    std::shared_ptr<AsyncResult> result = request->waitForResult(timeout);
    removePendingRequestUnlocked(message_id);

    int rc = result->rc;
    if (rc != MOSQ_ERR_SUCCESS) {
        loge("subscribe callback failed with code %d: %s\n", rc, mosquitto_strerror(rc));
        destroyUnlocked();
        mosquitto_property_free_all(&properties);
        throw MqttException("couldn't subscribe", rc);
    }

    {
        std::ostringstream imploded;
        std::copy(filters.begin(), filters.end(), std::ostream_iterator<std::string>(imploded, " "));
        logd("Subscribed on '%s' filters QoS %d message id %d\n", imploded.str().c_str(), qos, message_id);
    }

    mosquitto_property_free_all(&properties);

    return result->granted_qos;
}

void MqttConnection::on_subscribe(struct mosquitto *, void * obj, int mid, int qos_count, const int * granted_qos, const mosquitto_property * props) {
    ((MqttConnection*)obj)->onSubscribe(mid, qos_count, granted_qos, props);
}

void MqttConnection::onSubscribe(int mid, int qos_count, const int * granted_qos, const mosquitto_property * props) {
    logd("onSubscribe mid %d qos_count %d granted_qos %p props %p\n", mid, qos_count, granted_qos, props);

    std::lock_guard<std::mutex> lk(m_mutex);

    PendingRequest * request = getValidPendingRequestLocked(mid);
    if (request) {
        std::shared_ptr<AsyncResult> result(new AsyncResult(mid, qos_count, granted_qos, props));
        request->submitResult(result);
    }
}


std::vector<int> MqttConnection::unsubscribe(unsigned timeout, const std::vector<std::string> & filters) {
    PendingRequest * request = 0;
    int message_id = 0;

    std::vector<const char*> pointers;
    for (const std::string & filter : filters) {
        pointers.push_back(filter.c_str());
    }

    {
        std::lock_guard<std::mutex> lk(m_mutex);
        stateCheck();


        // TODO: pass also user properties
        int rc = mosquitto_unsubscribe_multiple(m_mosq, &message_id, pointers.size(), const_cast<char* const*>(pointers.data()), NULL);
        if (rc != MOSQ_ERR_SUCCESS) {
            loge("mosquitto_unsubscribe_multiple failed with code %d: %s\n", rc, mosquitto_strerror(rc));
            throw MqttException("couldn't unsubscribe", rc);
        }

        request = createPendingRequestLocked(message_id);
    }

    std::shared_ptr<AsyncResult> result = request->waitForResult(timeout);
    removePendingRequestUnlocked(message_id);

    int rc = result->rc;
    if (rc != MOSQ_ERR_SUCCESS) {
        loge("unsubscribe callback failed with code %d: %s\n", rc, mosquitto_strerror(rc));
        destroyUnlocked();
        throw MqttException("couldn't unsubscribe", rc);
    }

    {
        std::ostringstream imploded;
        std::copy(filters.begin(), filters.end(), std::ostream_iterator<std::string>(imploded, " "));
        logd("Unsubscribed from '%s' filters message id %d\n", imploded.str().c_str(), message_id);
    }

    // NOTE: mosquitto does not provides result code(s) from unsubscribe; produce vector of successes
    return std::vector<int> (filters.size(), 0);
}

void MqttConnection::on_unsubscribe(struct mosquitto *, void * obj, int mid, const mosquitto_property * props) {
    ((MqttConnection*)obj)->onUnsubscribe(mid, props);
}

void MqttConnection::onUnsubscribe(int mid, const mosquitto_property * props) {
    logd("onUnsubscribe mid %d props %p\n", mid, props);

    std::lock_guard<std::mutex> lk(m_mutex);

    PendingRequest * request = getValidPendingRequestLocked(mid);
    if (request) {
        std::shared_ptr<AsyncResult> result(new AsyncResult(mid, props));
        request->submitResult(result);
    }
}


ClientControl::MqttPublishReply * MqttConnection::publish(unsigned timeout, int qos, bool is_retain, const std::string & topic, const std::string & payload) {
    PendingRequest * request = 0;
    int message_id = 0;
    {
        std::lock_guard<std::mutex> lk(m_mutex);
        stateCheck();

        // TODO: pass also user properties
        int rc = mosquitto_publish_v5(m_mosq, &message_id, topic.c_str(), payload.length(), payload.data(), qos, is_retain, NULL);
        if (rc != MOSQ_ERR_SUCCESS) {
            loge("mosquitto_publish_v5 failed with code %d: %s\n", rc, mosquitto_strerror(rc));
            throw MqttException("couldn't publish", rc);
        }

        request = createPendingRequestLocked(message_id);
    }

    std::shared_ptr<AsyncResult> result = request->waitForResult(timeout);
    removePendingRequestUnlocked(message_id);

    logd("Published to '%s' QoS %d  id %d\n", topic.c_str(), qos, message_id);
    return convertToPublishReply(result->rc, result->props);
}

void MqttConnection::on_publish(struct mosquitto *, void * obj, int mid, int rc, const mosquitto_property * props) {
    ((MqttConnection*)obj)->onPublish(mid, rc, props);
}

void MqttConnection::onPublish(int mid, int rc, const mosquitto_property * props) {
    logd("onPublish mid %d rc %d props %p\n", mid, rc, props);

    std::lock_guard<std::mutex> lk(m_mutex);

    PendingRequest * request = getValidPendingRequestLocked(mid);
    if (request) {
        std::shared_ptr<AsyncResult> result(new AsyncResult(mid, props));
        request->submitResult(result);
    }
}

void MqttConnection::on_message(struct mosquitto *, void * obj, const struct mosquitto_message * message, const mosquitto_property * props) {
    ((MqttConnection*)obj)->onMessage(message, props);
}

void MqttConnection::onMessage(const struct mosquitto_message * message, const mosquitto_property * props) {
    logd("onMessage message %p props %p\n", message, props);
    ClientControl::Mqtt5Message * msg = convertToMqtt5Message(message, props);
    m_grpc_client.onReceiveMqttMessage(m_connection_id, msg);
}

void MqttConnection::on_log(struct mosquitto *, void *, int level, const char * str) {
    if (level & MOSQ_LOG_DEBUG) {
        logd("mosquitto: %s\n", str);
    } else if (level & MOSQ_LOG_ERR) {
        loge("mosquitto: %s\n", str);
    } else if (level & MOSQ_LOG_WARNING) {
        logw("mosquitto: %s\n", str);
    } else if (level & MOSQ_LOG_NOTICE) {
        logn("mosquitto: %s\n", str);
    } else if (level & MOSQ_LOG_INFO) {
        logi("mosquitto: %s\n", str);
    }
}

void MqttConnection::destroyLocked() {
    if (m_mosq) {
        mosquitto_destroy(m_mosq);
        mosquitto_loop_stop(m_mosq, false);
        m_mosq = NULL;
    }

    if (!m_ca_file.empty()) {
        removeFile(m_ca_file);
        m_ca_file.clear();
    }

    if (!m_cert_file.empty()) {
        removeFile(m_cert_file);
        m_cert_file.clear();
    }

    if (!m_key_file.empty()) {
        removeFile(m_key_file);
    }
}

void MqttConnection::destroyUnlocked() {
    std::lock_guard<std::mutex> lk(m_mutex);
    destroyLocked();
}

void MqttConnection::removeFile(std::string & file) {
    if (!file.empty()) {
        std::remove(file.c_str());
        file.clear();
    }
}

// FIXME: place credentials to temporary files is dangerous
std::string MqttConnection::saveToTempFile(const std::string & content) {
    char buffer[1024];
    char * filename = tmpnam(buffer);                                   // TODO: replace unsafe tmpnam()
    if (filename) {
        FILE* fp = fopen(filename, "w");
        if (fp) {
            int rc = fputs(content.c_str(), fp);
            int error = errno;
            fclose(fp);
            if (rc == EOF) {
                throw MqttException("couldn't write to temporary file", error);        // TODO: add filename
            }
            return std::string(filename);
        } else {
            throw MqttException("couldn't create temporary file for TLS credentials", errno);
        }
    } else {
        throw MqttException("couldn't generate temporary file name for TLS credentials", errno);
    }
}

void MqttConnection::stateCheck() {
    if (!m_mosq) {
        throw MqttException("MQTT client is not in connected state", -1);
    }
}


PendingRequest * MqttConnection::createPendingRequestLocked(int request_id) {
    PendingRequest * request = new PendingRequest();
    auto it = m_requests.find(request_id);
    // remove old
    if (it != m_requests.end()) {
        delete it->second;
    }
    // insert new
    m_requests[request_id] = request;
    return request;
}

PendingRequest * MqttConnection::getValidPendingRequestLocked(int request_id) {
    PendingRequest * request = NULL;

    auto it = m_requests.find(request_id);
    if (it != m_requests.end() && it->second->isValid()) {
        request = it->second;
    }

    return request;
}

void MqttConnection::removePendingRequestUnlocked(int request_id) {
    std::lock_guard<std::mutex> lk(m_mutex);

    removePendingRequestLocked(request_id);
}

void MqttConnection::removePendingRequestLocked(int request_id) {

    auto it = m_requests.find(request_id);
    if (it != m_requests.end()) {
        delete it->second;
        m_requests.erase(it);
    }
}

ClientControl::Mqtt5ConnAck * MqttConnection::convertToConnack(int reason_code, int flags, const mosquitto_property * props) {
    uint32_t value32;
    uint16_t value16;
    uint8_t value8;
    char * value_str;

    ClientControl::Mqtt5ConnAck * conn_ack = new ClientControl::Mqtt5ConnAck();
    conn_ack->set_reasoncode(reason_code);
    // in both MQTT 3.1.1 and 5.0 Session present is bit 0 of flags

    conn_ack->set_sessionpresent(flags & 0x1);

    for (const mosquitto_property * prop = props; prop != NULL; prop = mosquitto_property_next(prop)) {
        int id = mosquitto_property_identifier(prop);
        switch (id) {
            case MQTT_PROP_SESSION_EXPIRY_INTERVAL:
                mosquitto_property_read_int32(prop, id, &value32, false);
                conn_ack->set_sessionexpiryinterval(value32);
                break;
            case MQTT_PROP_RECEIVE_MAXIMUM:
                mosquitto_property_read_int16(prop, id, &value16, false);
                conn_ack->set_receivemaximum(value16);
                break;
            case MQTT_PROP_MAXIMUM_QOS:
                mosquitto_property_read_byte(prop, id, &value8, false);
                conn_ack->set_maximumqos(value8);
                break;
            case MQTT_PROP_RETAIN_AVAILABLE:
                mosquitto_property_read_byte(prop, id, &value8, false);
                conn_ack->set_retainavailable(value8);
                break;
            case MQTT_PROP_MAXIMUM_PACKET_SIZE:
                mosquitto_property_read_int32(prop, id, &value32, false);
                conn_ack->set_maximumpacketsize(value32);
                break;
            case MQTT_PROP_ASSIGNED_CLIENT_IDENTIFIER:
                mosquitto_property_read_string(prop, id, &value_str, false);
                conn_ack->set_assignedclientid(value_str);
                break;
            case MQTT_PROP_REASON_STRING:
                mosquitto_property_read_string(prop, id, &value_str, false);
                conn_ack->set_reasonstring(value_str);
                break;
            case MQTT_PROP_WILDCARD_SUB_AVAILABLE:
                mosquitto_property_read_byte(prop, id, &value8, false);
                conn_ack->set_wildcardsubscriptionsavailable(value8);
                break;
            case MQTT_PROP_SUBSCRIPTION_ID_AVAILABLE:
                mosquitto_property_read_byte(prop, id, &value8, false);
                conn_ack->set_subscriptionidentifiersavailable(value8);
                break;
            case MQTT_PROP_SHARED_SUB_AVAILABLE:
                mosquitto_property_read_byte(prop, id, &value8, false);
                conn_ack->set_sharedsubscriptionsavailable(value8);
                break;
            case MQTT_PROP_SERVER_KEEP_ALIVE:
                mosquitto_property_read_int16(prop, id, &value16, false);
                conn_ack->set_serverkeepalive(value16);
                break;
            case MQTT_PROP_RESPONSE_INFORMATION:
                mosquitto_property_read_string(prop, id, &value_str, false);
                conn_ack->set_responseinformation(value_str);
                break;
            case MQTT_PROP_SERVER_REFERENCE:
                mosquitto_property_read_string(prop, id, &value_str, false);
                conn_ack->set_serverreference(value_str);
                break;
            case MQTT_PROP_TOPIC_ALIAS_MAXIMUM:
                mosquitto_property_read_int16(prop, id, &value16, false);
                conn_ack->set_topicaliasmaximum(value16);
                break;
            default:
                logw("Unhandled CONNACK property with id %d\n", id);
                break;
        }
    }

    return conn_ack;
}

ClientControl::MqttPublishReply * MqttConnection::convertToPublishReply(int reason_code, const mosquitto_property * props) {
    char * value_str;

    ClientControl::MqttPublishReply * reply = new ClientControl::MqttPublishReply();
    reply->set_reasoncode(reason_code);

    for (const mosquitto_property * prop = props; prop != NULL; prop = mosquitto_property_next(prop)) {
        int id = mosquitto_property_identifier(prop);
        switch (id) {
            case MQTT_PROP_REASON_STRING:
                mosquitto_property_read_string(prop, id, &value_str, false);
                reply->set_reasonstring(value_str);
                break;
            default:
                logw("Unhandled PUBACK property with id %d\n", id);
                break;
        }
    }

    return reply;
}

ClientControl::Mqtt5Message * MqttConnection::convertToMqtt5Message(const struct mosquitto_message * message, const mosquitto_property * props) {

    (void)props;

    ClientControl::Mqtt5Message * msg = new ClientControl::Mqtt5Message();

    if (message) {
        if (message->topic) {
            msg->set_topic(message->topic);
        }

        if (message->payload && message->payloadlen > 0) {
            std::string payload_copy(std::string(static_cast<const char*>(message->payload), message->payloadlen));
            msg->set_payload(payload_copy);
        }

        if (ClientControl::MqttQoS_IsValid(message->qos)) {
            msg->set_qos(static_cast<ClientControl::MqttQoS>(message->qos));
        }
        msg->set_retain(message->retain);
    }

    // TODO: handle also props to find and copy user properties

    return msg;
}

ClientControl::Mqtt5Disconnect * MqttConnection::convertToDisconnect(int reason_code, const mosquitto_property * props) {
    uint32_t value32;
    char * value_str;

    ClientControl::Mqtt5Disconnect * disconnect = new ClientControl::Mqtt5Disconnect();

    disconnect->set_reasoncode(reason_code);

    for (const mosquitto_property * prop = props; prop != NULL; prop = mosquitto_property_next(prop)) {
        int id = mosquitto_property_identifier(prop);
        switch (id) {
            case MQTT_PROP_SESSION_EXPIRY_INTERVAL:
                mosquitto_property_read_int32(prop, id, &value32, false);
                disconnect->set_sessionexpiryinterval(value32);
                break;
            case MQTT_PROP_REASON_STRING:
                mosquitto_property_read_string(prop, id, &value_str, false);
                disconnect->set_reasonstring(value_str);
                break;
            case MQTT_PROP_SERVER_REFERENCE:
                mosquitto_property_read_string(prop, id, &value_str, false);
                disconnect->set_serverreference(value_str);
                break;
            // TODO: handle also user properties
            default:
                logw("Unhandled DISCONNECT property with id %d\n", id);
                break;
        }
    }

    return disconnect;
}
