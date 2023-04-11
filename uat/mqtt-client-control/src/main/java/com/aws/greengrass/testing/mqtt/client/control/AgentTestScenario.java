/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt.client.control;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Disconnect;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5RetainHandling;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Subscription;
import com.aws.greengrass.testing.mqtt.client.MqttConnectRequest;
import com.aws.greengrass.testing.mqtt.client.MqttProtoVersion;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttQoS;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import com.aws.greengrass.testing.mqtt.client.TLSSettings;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl;
import com.aws.greengrass.testing.mqtt.client.control.api.AgentControl.ConnectionEvents;
import com.aws.greengrass.testing.mqtt.client.control.api.ConnectionControl;
import com.aws.greengrass.testing.mqtt.client.control.api.addon.EventStorage;
import com.aws.greengrass.testing.mqtt.client.control.implementation.addon.MqttMessageEvent;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Class with hardcoded scenario to do manual tests of client and control.
 */
class AgentTestScenario implements Runnable {
    // pauses in milliseconds
    private static final long PAUSE_BEFORE_CONNECT = 3_000;
    private static final long PAUSE_BEFORE_SUBSCRIBE = 5_000;
    private static final long PAUSE_BEFORE_PUBLISH = 5_000;
    private static final long PAUSE_BEFORE_UNSUBSCRIBE = 5_000;
    private static final long PAUSE_BEFORE_DISCONNECT = 15_000;


    private static final String MQTT_CLIENT_ID = "MQTT_CLIENT_ID";
    private static final String DEFAULT_MQTT_CLIENT_ID = "MQTT_Client_1";

    private static final String MQTT_BROKER_ADDR = "MQTT_BROKER_ADDR";
    private static final String DEFAULT_BROKER_HOST = "localhost";

    private static final String MQTT_BROKER_PORT = "MQTT_BROKER_PORT";
    private static final String DEFAULT_BROKER_PORT = "8883";

    private static final String CA_FILE = "MQTT_CLIENT_CA_FILE";
    private static final String DEFAULT_CA_FILE = "ca.crt";

    private static final String CERT_FILE = "MQTT_CLIENT_CERT_FILE";
    private static final String DEFAULT_CERT_FILE = "client.crt";

    private static final String KEY_FILE = "MQTT_CLIENT_KEY_FILE";
    private static final String DEFAULT_KEY_FILE = "client.key";

    private static final int KEEP_ALIVE = 60;
    private static final boolean CLEAN_SESSION = true;
    private static final int CONNECT_TIMEOUT = 30;

    private static final int DISCONNECT_REASON = 4;

    private static final String PUBLISH_TOPIC = "test/topic";
    private static final String PUBLISH_TEXT = "Hello World!";
    private static final MqttQoS PUBLISH_QOS = MqttQoS.MQTT_QOS_1;
    private static final boolean PUBLISH_RETAIN = false;

    private static final Integer SUBSCRIPTION_ID = null;                        // NOTE: do not set for IoT Core !!!
    private static final String SUBSCRIBE_FILTER = "test/topic";
    private static final MqttQoS SUBSCRIBE_QOS = MqttQoS.MQTT_QOS_0;
    private static final boolean SUBSCRIBE_NO_LOCAL = false;
    private static final boolean SUBSCRIBE_RETAIN_AS_PUBLISHED = false;
    private static final Mqtt5RetainHandling SUBSCRIBE_RETAIN_HANDLING
            = Mqtt5RetainHandling.MQTT5_RETAIN_DO_NOT_SEND_AT_SUBSCRIPTION;

    private static final Logger logger = LogManager.getLogger(AgentTestScenario.class);

    private boolean useTLS;
    private final AgentControl agentControl;
    private final EventStorage eventStorage;

    private String ca = null;
    private String cert = null;
    private String key = null;

    private final ConnectionEvents connectionEvents = new ConnectionEvents() {
        @Override
        public void onMessageReceived(ConnectionControl connectionControl, Mqtt5Message msg) {
            logger.atInfo().log("Message received on agentId {} connectionId {} topic {} QoS {} content {}",
                                agentControl.getAgentId(),
                                connectionControl.getConnectionId(),
                                msg.getTopic(),
                                msg.getQos().getNumber(),
                                msg.getPayload());

            eventStorage.addEvent(new MqttMessageEvent(connectionControl, msg));
        }

        @Override
        public void onMqttDisconnect(ConnectionControl connectionControl, Mqtt5Disconnect disconnect, String error) {
            logger.atInfo().log("MQTT disconnected on agentId {} connectionId {} disconnect '{}' error '{}'",
                                agentControl.getAgentId(),
                                connectionControl.getConnectionId(),
                                disconnect,
                                error);
        }
    };

    public AgentTestScenario(boolean useTLS, AgentControl agentControl, EventStorage eventStorage) {
        super();

        this.useTLS = useTLS;
        this.agentControl = agentControl;
        this.eventStorage = eventStorage;

        if (useTLS) {
            this.ca = readFile(getCaFile());
            this.cert = readFile(getCertFile());
            this.key = readFile(getKeyFile());
        }
    }

    @Override
    public void run() {
        ConnectionControl connectionControl = null;
        try {
            logger.atInfo().log("Playing test scenario for agent id {}", agentControl.getAgentId());

            Thread.sleep(PAUSE_BEFORE_CONNECT);

            // create MQTT connection
            MqttConnectRequest connectRequest = getMqttConnectRequest();
            connectionControl = agentControl.createMqttConnection(connectRequest, connectionEvents);
            logger.atInfo().log("MQTT connection with id {} is established", connectionControl.getConnectionId());

            Thread.sleep(PAUSE_BEFORE_SUBSCRIBE);
            testSubscribe(connectionControl);

            Thread.sleep(PAUSE_BEFORE_PUBLISH);
            testPublish(connectionControl);

            Thread.sleep(PAUSE_BEFORE_UNSUBSCRIBE);
            testUnsubscribe(connectionControl);

            Thread.sleep(PAUSE_BEFORE_DISCONNECT);
        } catch (InterruptedException ex) {
            logger.atError().withThrowable(ex).log("InterruptedException");
        } catch (StatusRuntimeException ex) {
            Status status = ex.getStatus();
            logger.atError().withThrowable(ex).log("gRPC error code {}: description: {}",
                                                        status.getCode(), status.getDescription());
        } finally {
            if (connectionControl != null) {
                // close MQTT connection
                connectionControl.closeMqttConnection(DISCONNECT_REASON);
            }
            agentControl.shutdownAgent("That's it.");
        }
    }

    private MqttConnectRequest getMqttConnectRequest() {
        // TODO: set CONNECT properties
        // TODO: set willMessage
        MqttConnectRequest.Builder builder = MqttConnectRequest.newBuilder()
                    .setClientId(getClientId())
                    .setHost(getBrokerAddress())
                    .setPort(getBrokerPort())
                    .setKeepalive(KEEP_ALIVE)
                    .setCleanSession(CLEAN_SESSION)
                    .setTimeout(CONNECT_TIMEOUT)
                    .setProtocolVersion(MqttProtoVersion.MQTT_PROTOCOL_V50);

        if (useTLS) {
            TLSSettings tlsSettings = TLSSettings.newBuilder().setCa(ca).setCert(cert).setKey(key).build();
            builder.setTls(tlsSettings);
        }

        return builder.build();
    }


    private void testSubscribe(ConnectionControl connectionControl) {
        Mqtt5Subscription subscription = createSubscription(SUBSCRIBE_FILTER, SUBSCRIBE_QOS, SUBSCRIBE_NO_LOCAL,
                                                            SUBSCRIBE_RETAIN_AS_PUBLISHED, SUBSCRIBE_RETAIN_HANDLING);

        MqttSubscribeReply reply = connectionControl.subscribeMqtt(SUBSCRIPTION_ID, subscription);
        logger.atInfo().log("Subscribe response: connectionId {} reason codes {} reason string '{}'",
                                connectionControl.getConnectionId(),
                                reply.getReasonCodesList(),
                                reply.getReasonString());
    }

    private Mqtt5Subscription createSubscription(String filter, MqttQoS qos, boolean noLocal,
                                                    boolean retainAsPublished, Mqtt5RetainHandling retainHandling) {
        return Mqtt5Subscription.newBuilder()
                    .setFilter(filter)
                    .setQos(qos)
                    .setNoLocal(noLocal)
                    .setRetainAsPublished(retainAsPublished)
                    .setRetainHandling(retainHandling)
                    .build();
    }


    private void testPublish(ConnectionControl connectionControl) {
        Mqtt5Message msg = createPublishMessage(PUBLISH_QOS, PUBLISH_RETAIN, PUBLISH_TOPIC, PUBLISH_TEXT.getBytes());
        MqttPublishReply reply = connectionControl.publishMqtt(msg);
        logger.atInfo().log("Published connectionId {} reasonCode {} reasonString {}",
                                connectionControl.getConnectionId(), reply.getReasonCode(), reply.getReasonCode());
    }

    private Mqtt5Message createPublishMessage(MqttQoS qos, boolean retain, String topic, byte[] data) {
        return Mqtt5Message.newBuilder()
                            .setTopic(topic)
                            .setPayload(ByteString.copyFrom(data))
                            .setQos(qos)
                            .setRetain(retain)
                            .build();
    }

    private void testUnsubscribe(ConnectionControl connectionControl) {
        MqttSubscribeReply reply = connectionControl.unsubscribeMqtt(SUBSCRIBE_FILTER);
        logger.atInfo().log("Unsubscribe response: connectionId {} reason codes {} reason string '{}'",
                            connectionControl.getConnectionId(), reply.getReasonCodesList(), reply.getReasonString());
    }


    private String getClientId() {
        return getSetting(MQTT_CLIENT_ID.toLowerCase(), MQTT_CLIENT_ID, DEFAULT_MQTT_CLIENT_ID);
    }

    private String getBrokerAddress() {
        return getSetting(MQTT_BROKER_ADDR.toLowerCase(), MQTT_BROKER_ADDR, DEFAULT_BROKER_HOST);
    }

    private int getBrokerPort() {
        String port = getSetting(MQTT_BROKER_PORT.toLowerCase(), MQTT_BROKER_PORT, DEFAULT_BROKER_PORT);
        return Integer.valueOf(port);
    }

    private Path getCaFile() {
        return Paths.get(getSetting(CA_FILE.toLowerCase(), CA_FILE, DEFAULT_CA_FILE));
    }

    private Path getCertFile() {
        return Paths.get(getSetting(CERT_FILE.toLowerCase(), CERT_FILE, DEFAULT_CERT_FILE));
    }

    private Path getKeyFile() {
        return Paths.get(getSetting(KEY_FILE.toLowerCase(), KEY_FILE, DEFAULT_KEY_FILE));
    }

    private String getSetting(String propertyName, String envName, String defaultValue) {
        String value = System.getProperty(propertyName);
        if (value != null) {
            return value;
        }

        value = System.getenv(envName);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }

    private String readFile(Path file) {
        try {
            byte[] encoded = Files.readAllBytes(file);
            return new String(encoded, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            logger.atError().withThrowable(ex).log("Couldn't read file {}, continue without TLS", file);
            useTLS = false;
        }
        return null;
    }
}

