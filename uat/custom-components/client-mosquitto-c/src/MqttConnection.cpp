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

#define CONNECT_REQUEST_ID              (1024*64 + 1)
#define DISCONNECT_REQUEST_ID           (1024*64 + 2)

class AsyncResult {
public:
    int rc;                                     // all
    int flags;                                  // connect
    mosquitto_property * props;                 // all
    int mid;                                    // subscribe / publish / unsubscribe
    std::vector<int> granted_qos;               // subscribe

    // subscribe
    AsyncResult(int mid_, int qos_count, const int * granted_qos_arr, const mosquitto_property * props_)
        : rc(MOSQ_ERR_SUCCESS), flags(0), props(NULL), mid(mid_), granted_qos(granted_qos_arr, granted_qos_arr + qos_count) {
        mosquitto_property_copy_all(&props, props_);
    }

    // connect / disconnect / unsubscribe / publish
    AsyncResult(int rc_, const mosquitto_property * props_, int flags_ = 0, int mid_ = 0)
        :  rc(rc_), flags(flags_), props(NULL), mid(mid_), granted_qos() {
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


MqttConnection::MqttConnection(GRPCDiscoveryClient & grpc_client, const std::string & client_id, const std::string & host,
                                unsigned short port, unsigned short keepalive, bool clean_session, const char * ca,
                                const char * cert, const char * key, bool v5,
                                const RepeatedPtrField<ClientControl::Mqtt5Properties> & user_properties,
                                const bool * rri)
    : m_mutex(), m_grpc_client(grpc_client), m_client_id(client_id), m_host(host), m_port(port), m_keepalive(keepalive),
        m_clean_session(clean_session), m_v5(v5), m_connection_id(0), m_is_closing(false), m_is_connected(false),
        m_conn_properties(0), m_request_response_information(0), m_mosq(0), m_requests() {

    logd("Creating Mosquitto MQTT v%d connection for %s:%hu\n", m_v5 ? 5 : 3, m_host.c_str(), m_port);

    if (ca) {
        m_ca = ca;
    }

    if (cert) {
        m_cert = cert;
    }

    if (key) {
        m_key = key;
    }

    if (rri) {
        m_request_response_information = new bool(rri);
    }

    convertUserProperties(&user_properties, &m_conn_properties);
}

MqttConnection::~MqttConnection() {
    logd("Destroy Mosquitto MQTT connection\n");
    if (!m_is_closing.load()) {
        disconnect(DEFAULT_DISCONNECT_TIMEOUT, DEFAULT_DISCONNECT_REASON, NULL);
    }

    if (m_request_response_information) {
        delete m_request_response_information;
    }
}

ClientControl::Mqtt5ConnAck * MqttConnection::start(unsigned timeout) {
    int rc;
    PendingRequest * request = 0;

    logd("Establishing Mosquitto MQTT v%d connection to %s:%hu in %d seconds\n", m_v5 ? 5 : 3, m_host.c_str(), m_port, timeout);

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

        if (m_request_response_information) {
            if (m_v5) {
                uint8_t value = *m_request_response_information ? 1 : 0;
                rc = mosquitto_property_add_byte(&m_conn_properties, MQTT_PROP_REQUEST_RESPONSE_INFORMATION, value);
                if (rc == MOSQ_ERR_SUCCESS) {
                    logd("Copied TX request response information %d\n", value);
                } else {
                    loge("mosquitto_property_add_byte failed with code %d: %s\n", rc, mosquitto_strerror(rc));
                    throw MqttException("couldn't set request response information", rc);
                }
            } else {
                logw("Request response information is ignored for MQTT v3.1.1 connection\n");
            }
        }

        mosquitto_connect_v5_callback_set(m_mosq, on_connect);
        mosquitto_disconnect_v5_callback_set(m_mosq, on_disconnect);

        mosquitto_publish_v5_callback_set(m_mosq, on_publish);
        mosquitto_message_v5_callback_set(m_mosq, on_message);

        mosquitto_subscribe_v5_callback_set(m_mosq, on_subscribe);
        mosquitto_unsubscribe_v5_callback_set(m_mosq, on_unsubscribe);


        mosquitto_log_callback_set(m_mosq, on_log);

        mosquitto_reconnect_delay_set(m_mosq, RECONNECT_DELAY_SEC, RECONNECT_DELAY_SEC, true);

        if (!m_ca.empty() && !m_cert.empty() && !m_key.empty()) {
            logd("Use provided TLS credentials\n");
            m_ca_file = saveToTempFile(m_ca);
            m_ca.clear();

            m_cert_file = saveToTempFile(m_cert);
            m_cert.clear();

            m_key_file = saveToTempFile(m_key);
            m_key.clear();

            rc = mosquitto_tls_set(m_mosq, m_ca_file.c_str(), NULL, m_cert_file.c_str(), m_key_file.c_str(), NULL);
            if (rc != MOSQ_ERR_SUCCESS) {
                loge("mosquitto_tls_set failed with code %d: %s\n", rc, mosquitto_strerror(rc));
                destroyLocked();
                throw MqttException("couldn't set TLS credentials", rc);
            }
            // TODO: select TLS version with mosquitto_tls_opts_set();
            // NOTE: enable for tests only
            // mosquitto_tls_insecure_set(m_mosq, true);
        } else {
            logd("TLS credentials does not provided, continue without encryption\n");
        }

        // TODO
        // mosquitto_will_set_v5()

        rc = mosquitto_loop_start(m_mosq);
        if (rc != MOSQ_ERR_SUCCESS) {
            loge("mosquitto_loop_start failed with code %d: %s\n", rc, mosquitto_strerror(rc));
            destroyLocked();
            throw MqttException("couldn't start interval library thread", rc);
        }

        // FIXME: there is no async v5 connect() !!!
        rc = mosquitto_connect_bind_v5(m_mosq, m_host.c_str(), m_port, m_keepalive, NULL, m_conn_properties);
        // rc = mosquitto_connect_bind_async(m_mosq, m_host.c_str(), m_port, m_keepalive, NULL);
        if (rc != MOSQ_ERR_SUCCESS) {
            loge("mosquitto_connect_bind_v5 failed with code %d: %s\n", rc, mosquitto_strerror(rc));
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

void MqttConnection::onConnect(int rc, int flags, const mosquitto_property * props) {
    m_is_connected.store(true);

    logd("onConnect rc %d flags %d props %p\n", rc, flags, props);

    std::lock_guard<std::mutex> lk(m_mutex);

    PendingRequest * request = getValidPendingRequestLocked(CONNECT_REQUEST_ID);
    if (request) {
        std::shared_ptr<AsyncResult> result(new AsyncResult(rc, props, flags));
        request->submitResult(result);
    }
}

void MqttConnection::disconnect(unsigned timeout, unsigned char reason_code, const RepeatedPtrField<ClientControl::Mqtt5Properties> * user_properties) {

    bool expected_is_closing = false;
    if (!m_is_closing.compare_exchange_strong(expected_is_closing, true)) {
        logw("Ignore secondary DISCONNECT request(s)\n");
        return;
    }

    PendingRequest * request = 0;
    mosquitto_property * properties = NULL;
    {
        std::lock_guard<std::mutex> lk(m_mutex);

        if (m_mosq) {
            if (m_is_connected.load()) {
                logd("Disconnect Mosquitto MQTT connection with reason code %d\n", (int)reason_code);

                convertUserProperties(user_properties, &properties);

                // FIXME: there isn't async disconnect()
                int rc = mosquitto_disconnect_v5(m_mosq, reason_code, properties);
                if (rc != MOSQ_ERR_SUCCESS) {
                    mosquitto_property_free_all(&properties);
                    throw MqttException("couldn't disconnect from MQTT broker", rc);
                }
                request = createPendingRequestLocked(DISCONNECT_REQUEST_ID);
            }
        } else {
            logw("DISCONNECT was not sent on the dead connection\n");
        }
    }

    if (request) {
        std::shared_ptr<AsyncResult> result = request->waitForResult(timeout);

        removePendingRequestUnlocked(DISCONNECT_REQUEST_ID);
        destroyUnlocked();

        int rc = result->rc;
        if (rc != MOSQ_ERR_SUCCESS) {
            mosquitto_property_free_all(&properties);
            throw MqttException("couldn't disconnect from MQTT broker", rc);
        }
    }
    mosquitto_property_free_all(&properties);
}

void MqttConnection::on_disconnect(struct mosquitto *, void * obj, int rc, const mosquitto_property * props) {
    ((MqttConnection*)obj)->onDisconnect(rc, props);
}

void MqttConnection::onDisconnect(int rc, const mosquitto_property * props) {
    m_is_connected.store(false);

    logd("onDisconnect rc %d props %p\n", rc, props);

    ClientControl::Mqtt5Disconnect * disconnect = convertToDisconnect(rc, props);
    m_grpc_client.onMqttDisconnect(m_connection_id, disconnect, NULL);

    std::lock_guard<std::mutex> lk(m_mutex);

    PendingRequest * request = getValidPendingRequestLocked(DISCONNECT_REQUEST_ID);
    if (request) {
        std::shared_ptr<AsyncResult> result(new AsyncResult(rc, props));
        request->submitResult(result);
    }
}

void MqttConnection::subscribe(unsigned timeout, const int * subscription_id,
                                const std::vector<std::string> & filters, int qos, int retain_handling,
                                bool no_local, bool retain_as_published,
                                const RepeatedPtrField<ClientControl::Mqtt5Properties> & user_properties,
                                ClientControl::MqttSubscribeReply * reply) {
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

        convertUserProperties(&user_properties, &properties);

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
        mosquitto_property_free_all(&properties);
        throw MqttException("couldn't subscribe", rc);
    }

    {
        std::ostringstream imploded;
        std::copy(filters.begin(), filters.end(), std::ostream_iterator<std::string>(imploded, ","));
        logd("Subscribed on filters '%s' QoS %d no local %d retain as published %d retain handing %d with message id %d\n", imploded.str().c_str(), qos, no_local, retain_as_published, retain_handling, message_id);
    }

    mosquitto_property_free_all(&properties);

    updateMqttSubscribeReply(result->granted_qos, result->props, reply);
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


void MqttConnection::unsubscribe(unsigned timeout, const std::vector<std::string> & filters,
                                    const RepeatedPtrField<ClientControl::Mqtt5Properties> & user_properties,
                                    ClientControl::MqttSubscribeReply * reply) {
    PendingRequest * request = 0;
    int message_id = 0;
    mosquitto_property * properties = NULL;

    std::vector<const char*> pointers;
    for (const std::string & filter : filters) {
        pointers.push_back(filter.c_str());
    }

    {
        std::lock_guard<std::mutex> lk(m_mutex);
        stateCheck();

        convertUserProperties(&user_properties, &properties);

        // TODO: pass also user properties
        int rc = mosquitto_unsubscribe_multiple(m_mosq, &message_id, pointers.size(), const_cast<char* const*>(pointers.data()), properties);
        if (rc != MOSQ_ERR_SUCCESS) {
            loge("mosquitto_unsubscribe_multiple failed with code %d: %s\n", rc, mosquitto_strerror(rc));
            mosquitto_property_free_all(&properties);
            throw MqttException("couldn't unsubscribe", rc);
        }

        request = createPendingRequestLocked(message_id);
    }

    std::shared_ptr<AsyncResult> result = request->waitForResult(timeout);
    removePendingRequestUnlocked(message_id);

    int rc = result->rc;
    if (rc != MOSQ_ERR_SUCCESS) {
        loge("unsubscribe callback failed with code %d: %s\n", rc, mosquitto_strerror(rc));
        mosquitto_property_free_all(&properties);
        throw MqttException("couldn't unsubscribe", rc);
    }

    {
        std::ostringstream imploded;
        std::copy(filters.begin(), filters.end(), std::ostream_iterator<std::string>(imploded, ","));
        logd("Unsubscribed from filters '%s' with message id %d\n", imploded.str().c_str(), message_id);
    }


    mosquitto_property_free_all(&properties);

    // NOTE: mosquitto does not provide result code(s) from unsubscribe; produce vector of successes
    std::vector<int> success(filters.size(), 0);
    updateMqttSubscribeReply(success, result->props, reply);
}

void MqttConnection::on_unsubscribe(struct mosquitto *, void * obj, int mid, const mosquitto_property * props) {
    ((MqttConnection*)obj)->onUnsubscribe(mid, props);
}

void MqttConnection::onUnsubscribe(int mid, const mosquitto_property * props) {
    logd("onUnsubscribe mid %d props %p\n", mid, props);

    std::lock_guard<std::mutex> lk(m_mutex);

    PendingRequest * request = getValidPendingRequestLocked(mid);
    if (request) {
        std::shared_ptr<AsyncResult> result(new AsyncResult(MOSQ_ERR_SUCCESS, props, 0, mid));
        request->submitResult(result);
    }
}


ClientControl::MqttPublishReply * MqttConnection::publish(unsigned timeout, int qos, bool is_retain,
                                            const std::string & topic,
                                            const std::string & payload,
                                            const RepeatedPtrField<ClientControl::Mqtt5Properties> & user_properties,
                                            const std::string * content_type, const bool * payload_format_indicator,
                                            const int * message_expiry_interval, const std::string * response_topic,
                                            const std::string * correlation_data) {
    PendingRequest * request = 0;
    int message_id = 0;
    mosquitto_property * properties = NULL;

    {
        std::lock_guard<std::mutex> lk(m_mutex);
        stateCheck();

        int rc;

        // properties handled in the same order as noted in PUBLISH of MQTT v5.0 spec
        if (payload_format_indicator) {
            if (m_v5) {
                char value = *payload_format_indicator ? 1 : 0;
                rc = mosquitto_property_add_byte(&properties, MQTT_PROP_PAYLOAD_FORMAT_INDICATOR, value);
                if (rc == MOSQ_ERR_SUCCESS) {
                    logd("Copied TX payload format indicator %d\n", value);
                } else {
                    loge("mosquitto_property_add_byte failed with code %d: %s\n", rc, mosquitto_strerror(rc));
                    throw MqttException("couldn't set payload format indicator", rc);
                }
            } else {
                logw("Payload format indicator is ignored for MQTT v3.1.1 connection\n");
            }
        }

        if (message_expiry_interval) {
            if (m_v5) {
                rc = mosquitto_property_add_int32(&properties, MQTT_PROP_MESSAGE_EXPIRY_INTERVAL, *message_expiry_interval);
                if (rc == MOSQ_ERR_SUCCESS) {
                    logd("Copied TX message expiry interval %d\n", *message_expiry_interval);
                } else {
                    loge("mosquitto_property_add_int32 failed with code %d: %s\n", rc, mosquitto_strerror(rc));
                    throw MqttException("couldn't set message expiry interval", rc);
                }
            } else {
                logw("Message expiry interval is ignored for MQTT v3.1.1 connection\n");
            }
        }

        if (response_topic) {
            if (m_v5) {
                rc = mosquitto_property_add_string(&properties, MQTT_PROP_RESPONSE_TOPIC, response_topic->c_str());
                if (rc == MOSQ_ERR_SUCCESS) {
                    logd("Copied TX response topic \"%s\"\n", response_topic->c_str());
                } else {
                    loge("mosquitto_property_add_string failed with code %d: %s\n", rc, mosquitto_strerror(rc));
                    throw MqttException("couldn't set response topic", rc);
                }
            } else {
                logw("Response topic is ignored for MQTT v3.1.1 connection\n");
            }
        }

        if (correlation_data) {
            if (m_v5) {
                // NOTE: possible integer overflow when length >=2^16
                rc = mosquitto_property_add_binary(&properties, MQTT_PROP_CORRELATION_DATA, correlation_data->data(), correlation_data->length());
                if (rc == MOSQ_ERR_SUCCESS) {
                    logd("Copied TX correlation data \"%.*s\"\n", correlation_data->length(), correlation_data->c_str());
                } else {
                    loge("mosquitto_property_add_binary failed with code %d: %s\n", rc, mosquitto_strerror(rc));
                    throw MqttException("couldn't set correlation data", rc);
                }
            } else {
                logw("Correlation data is ignored for MQTT v3.1.1 connection\n");
            }
        }

        convertUserProperties(&user_properties, &properties);

        if (content_type) {
            if (m_v5) {
                rc = mosquitto_property_add_string(&properties, MQTT_PROP_CONTENT_TYPE, content_type->c_str());
                if (rc == MOSQ_ERR_SUCCESS) {
                    logd("Copied TX content type \"%s\"\n", content_type->c_str());
                } else {
                    loge("mosquitto_property_add_string failed with code %d: %s\n", rc, mosquitto_strerror(rc));
                    throw MqttException("couldn't set content type", rc);
                }
            } else {
                logw("Content type is ignored for MQTT v3.1.1 connection\n");
            }
        }

        rc = mosquitto_publish_v5(m_mosq, &message_id, topic.c_str(), payload.length(), payload.data(), qos, is_retain, properties);
        if (rc != MOSQ_ERR_SUCCESS) {
            loge("mosquitto_publish_v5 failed with code %d: %s\n", rc, mosquitto_strerror(rc));
            mosquitto_property_free_all(&properties);
            throw MqttException("couldn't publish", rc);
        }

        request = createPendingRequestLocked(message_id);
    }

    std::shared_ptr<AsyncResult> result = request->waitForResult(timeout);
    removePendingRequestUnlocked(message_id);

    logd("Published on topic '%s' QoS %d retain %d with rc %d message id %d\n", topic.c_str(), qos, is_retain, result->rc, message_id);
    mosquitto_property_free_all(&properties);
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
        std::shared_ptr<AsyncResult> result(new AsyncResult(rc, props, 0, mid));
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
    mosquitto_property_free_all(&m_conn_properties);
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
    char buffer[L_tmpnam + 1];
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
        throw MqttException("MQTT client connection was not created", -1);
    }

    if (!m_is_connected.load()) {
        throw MqttException("MQTT client is not connected", -1);
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
    char * name_str;
    char * value_str;
    ClientControl::Mqtt5Properties * new_user_property;

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
                logd("Copied RX session expire interval %d\n", value32);
                break;
            case MQTT_PROP_RECEIVE_MAXIMUM:
                mosquitto_property_read_int16(prop, id, &value16, false);
                conn_ack->set_receivemaximum(value16);
                logd("Copied RX receive maximum %d\n", value16);
                break;
            case MQTT_PROP_MAXIMUM_QOS:
                mosquitto_property_read_byte(prop, id, &value8, false);
                conn_ack->set_maximumqos(value8);
                logd("Copied RX maximum QoS %d\n", value8);
                break;
            case MQTT_PROP_RETAIN_AVAILABLE:
                mosquitto_property_read_byte(prop, id, &value8, false);
                conn_ack->set_retainavailable(value8);
                logd("Copied RX retain available %d\n", value8);
                break;
            case MQTT_PROP_MAXIMUM_PACKET_SIZE:
                mosquitto_property_read_int32(prop, id, &value32, false);
                conn_ack->set_maximumpacketsize(value32);
                logd("Copied RX maximum packet size %d\n", value32);
                break;
            case MQTT_PROP_ASSIGNED_CLIENT_IDENTIFIER:
                mosquitto_property_read_string(prop, id, &value_str, false);
                conn_ack->set_assignedclientid(value_str);
                logd("Copied RX assigned client identifier \"%s\"\n", value_str);
                free(value_str);
                break;
            case MQTT_PROP_REASON_STRING:
                mosquitto_property_read_string(prop, id, &value_str, false);
                conn_ack->set_reasonstring(value_str);
                logd("Copied RX reason string \"%s\"\n", value_str);
                free(value_str);
                break;
            case MQTT_PROP_WILDCARD_SUB_AVAILABLE:
                mosquitto_property_read_byte(prop, id, &value8, false);
                conn_ack->set_wildcardsubscriptionsavailable(value8);
                logd("Copied RX wildcard subscription available %d\n", value8);
                break;
            case MQTT_PROP_SUBSCRIPTION_ID_AVAILABLE:
                mosquitto_property_read_byte(prop, id, &value8, false);
                conn_ack->set_subscriptionidentifiersavailable(value8);
                logd("Copied RX subscription identifiers available %d\n", value8);
                break;
            case MQTT_PROP_SHARED_SUB_AVAILABLE:
                mosquitto_property_read_byte(prop, id, &value8, false);
                conn_ack->set_sharedsubscriptionsavailable(value8);
                logd("Copied RX shared subscription available %d\n", value8);
                break;
            case MQTT_PROP_SERVER_KEEP_ALIVE:
                mosquitto_property_read_int16(prop, id, &value16, false);
                conn_ack->set_serverkeepalive(value16);
                logd("Copied RX server keep alive %d\n", value16);
                break;
            case MQTT_PROP_RESPONSE_INFORMATION:
                mosquitto_property_read_string(prop, id, &value_str, false);
                conn_ack->set_responseinformation(value_str);
                logd("Copied RX response information \"%s\"\n", value_str);
                free(value_str);
                break;
            case MQTT_PROP_SERVER_REFERENCE:
                mosquitto_property_read_string(prop, id, &value_str, false);
                conn_ack->set_serverreference(value_str);
                logd("Copied RX server reference \"%s\"\n", value_str);
                free(value_str);
                break;
            case MQTT_PROP_TOPIC_ALIAS_MAXIMUM:
                mosquitto_property_read_int16(prop, id, &value16, false);
                conn_ack->set_topicaliasmaximum(value16);
                logd("Copied RX topic alias maximum %d\n", value16);
                break;
            case MQTT_PROP_USER_PROPERTY:
                mosquitto_property_read_string_pair(prop, id, &name_str, &value_str, false);
                new_user_property = conn_ack->add_properties();
                new_user_property->set_key(name_str);
                new_user_property->set_value(value_str);
                logd("Copied RX user property %s:%s\n", name_str, value_str);
                free(name_str);
                free(value_str);
                break;
            default:
                logw("Unhandled CONNACK property with id %d\n", id);
                break;
        }
    }

    return conn_ack;
}

ClientControl::MqttPublishReply * MqttConnection::convertToPublishReply(int reason_code, const mosquitto_property * props) {
    char * name_str;
    char * value_str;
    ClientControl::Mqtt5Properties * new_user_property;

    ClientControl::MqttPublishReply * reply = new ClientControl::MqttPublishReply();
    reply->set_reasoncode(reason_code);

    for (const mosquitto_property * prop = props; prop != NULL; prop = mosquitto_property_next(prop)) {
        int id = mosquitto_property_identifier(prop);
        switch (id) {
            case MQTT_PROP_REASON_STRING:
                mosquitto_property_read_string(prop, id, &value_str, false);
                reply->set_reasonstring(value_str);
                logd("Copied RX reason string \"%s\"\n", value_str);
                free(value_str);
                break;
            case MQTT_PROP_USER_PROPERTY:
                mosquitto_property_read_string_pair(prop, id, &name_str, &value_str, false);
                new_user_property = reply->add_properties();
                new_user_property->set_key(name_str);
                new_user_property->set_value(value_str);
                logd("Copied RX user property %s:%s\n", name_str, value_str);
                free(name_str);
                free(value_str);
                break;
            default:
                logw("Unhandled PUBACK property with id %d\n", id);
                break;
        }
    }

    return reply;
}

ClientControl::Mqtt5Message * MqttConnection::convertToMqtt5Message(const struct mosquitto_message * message, const mosquitto_property * props) {

    uint8_t value8;
    uint32_t value32;
    char * name_str;
    char * value_str;
    void * value_bin;
    uint16_t length_bin;
    ClientControl::Mqtt5Properties * new_user_property;

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

    for (const mosquitto_property * prop = props; prop != NULL; prop = mosquitto_property_next(prop)) {
        int id = mosquitto_property_identifier(prop);
        switch (id) {
            case MQTT_PROP_PAYLOAD_FORMAT_INDICATOR:
                mosquitto_property_read_byte(prop, id, &value8, false);
                msg->set_payloadformatindicator(value8);
                logd("Copied RX payload format indicator %d\n", value8);
                break;
            case MQTT_PROP_CONTENT_TYPE:
                mosquitto_property_read_string(prop, id, &value_str, false);
                msg->set_contenttype(value_str);
                logd("Copied RX content type \"%s\"\n", value_str);
                free(value_str);
                break;
            case MQTT_PROP_USER_PROPERTY:
                mosquitto_property_read_string_pair(prop, id, &name_str, &value_str, false);
                new_user_property = msg->add_properties();
                new_user_property->set_key(name_str);
                new_user_property->set_value(value_str);
                logd("Copied RX user property %s:%s\n", name_str, value_str);
                free(name_str);
                free(value_str);
                break;
            case MQTT_PROP_MESSAGE_EXPIRY_INTERVAL:
                mosquitto_property_read_int32(prop, id, &value32, false);
                msg->set_messageexpiryinterval(value32);
                logd("Copied RX message expiry interval %d\n", value32);
                break;
            case MQTT_PROP_RESPONSE_TOPIC:
                mosquitto_property_read_string(prop, id, &value_str, false);
                msg->set_responsetopic(value_str);
                logd("Copied RX response topic \"%s\"\n", value_str);
                free(value_str);
                break;
            case MQTT_PROP_CORRELATION_DATA:
                mosquitto_property_read_binary(prop, id, &value_bin, &length_bin, false);
                msg->set_correlationdata(value_bin, length_bin);
                logd("Copied RX correlation data \"%.*s\"\n", (int)length_bin, value_bin);
                free(value_bin);
                break;
            default:
                logw("Unhandled PUBLISH property with id %d\n", id);
                break;
        }
    }

    return msg;
}

ClientControl::Mqtt5Disconnect * MqttConnection::convertToDisconnect(int reason_code, const mosquitto_property * props) {
    uint32_t value32;
    char * name_str;
    char * value_str;
    ClientControl::Mqtt5Properties * new_user_property;

    ClientControl::Mqtt5Disconnect * disconnect = new ClientControl::Mqtt5Disconnect();

    disconnect->set_reasoncode(reason_code);

    for (const mosquitto_property * prop = props; prop != NULL; prop = mosquitto_property_next(prop)) {
        int id = mosquitto_property_identifier(prop);
        switch (id) {
            case MQTT_PROP_SESSION_EXPIRY_INTERVAL:
                mosquitto_property_read_int32(prop, id, &value32, false);
                disconnect->set_sessionexpiryinterval(value32);
                logd("Copied RX session expire interval %d\n", value32);
                break;
            case MQTT_PROP_REASON_STRING:
                mosquitto_property_read_string(prop, id, &value_str, false);
                disconnect->set_reasonstring(value_str);
                logd("Copied RX reason string \"%s\"\n", value_str);
                free(value_str);
                break;
            case MQTT_PROP_SERVER_REFERENCE:
                mosquitto_property_read_string(prop, id, &value_str, false);
                disconnect->set_serverreference(value_str);
                logd("Copied RX server reference \"%s\"\n", value_str);
                free(value_str);
                break;
            case MQTT_PROP_USER_PROPERTY:
                mosquitto_property_read_string_pair(prop, id, &name_str, &value_str, false);
                new_user_property = disconnect->add_properties();
                new_user_property->set_key(name_str);
                new_user_property->set_value(value_str);
                logd("Copied RX user property %s:%s\n", name_str, value_str);
                free(name_str);
                free(value_str);
                break;
            default:
                logw("Unhandled DISCONNECT property with id %d\n", id);
                break;
        }
    }

    return disconnect;
}

void MqttConnection::convertUserProperties(const RepeatedPtrField<ClientControl::Mqtt5Properties> * user_properties, mosquitto_property ** conn_properties) {

    if (!user_properties) {
        return;
    }

    if (m_v5) {
        for (const ClientControl::Mqtt5Properties & user_property : *user_properties) {
            const std::string & key = user_property.key();
            const std::string & value = user_property.value();

            // TODO: check is that reverse the list?
            mosquitto_property_add_string_pair(conn_properties, MQTT_PROP_USER_PROPERTY, key.c_str(), value.c_str());
            logd("Copied TX user property %s:%s\n", key.c_str(), value.c_str());
        }
    } else if (!user_properties->empty()) {
        logw("User properties are ignored for MQTT v3.1.1 connection\n");
    }
}

void MqttConnection::updateMqttSubscribeReply(const std::vector<int> & granted_qos, const mosquitto_property * props, ClientControl::MqttSubscribeReply * reply) {
    // copy reason codes
    for (int reason_code : granted_qos) {
        logd("Copied RX reason code %d\n", reason_code);
        reply->add_reasoncodes(reason_code);
    }

    char * name_str;
    char * value_str;
    ClientControl::Mqtt5Properties * new_user_property;
    // copy user properties
    for (const mosquitto_property * prop = props; prop != NULL; prop = mosquitto_property_next(prop)) {
        int id = mosquitto_property_identifier(prop);
        switch (id) {
            case MQTT_PROP_USER_PROPERTY:
                mosquitto_property_read_string_pair(prop, id, &name_str, &value_str, false);
                new_user_property = reply->add_properties();
                new_user_property->set_key(name_str);
                new_user_property->set_value(value_str);
                logd("Copied RX user property %s:%s\n", name_str, value_str);
                free(name_str);
                free(value_str);
                break;
            default:
                logw("Unhandled SUBACK/UNSUBACK property with id %d\n", id);
                break;
        }
    }
}
