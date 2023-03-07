/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Subscription;
import com.aws.greengrass.testing.mqtt.client.MqttClientControlGrpc;
import com.aws.greengrass.testing.mqtt.client.MqttCloseRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectionId;
import com.aws.greengrass.testing.mqtt.client.MqttProtoVersion;
import com.aws.greengrass.testing.mqtt.client.MqttPublishReply;
import com.aws.greengrass.testing.mqtt.client.MqttPublishRequest;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeReply;
import com.aws.greengrass.testing.mqtt.client.MqttSubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.MqttUnsubscribeRequest;
import com.aws.greengrass.testing.mqtt.client.ShutdownRequest;
import com.aws.greengrass.testing.mqtt.client.TLSSettings;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import com.google.protobuf.Empty;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of gRPC server handles requests of OTF.
 */
class GRPCControlServer {
    private static final Logger logger = LogManager.getLogger(GRPCControlServer.class);

    private static final String CONNECTION_WITH_DOES_NOT_FOUND = "connection with id {} doesn't found";
    private static final String CONNECTION_DOES_NOT_FOUND = "connection doesn't found";

    private static final int TIMEOUT_MIN = 1;

    private static final int QOS_MIN = 0;
    private static final int QOS_MAX = 3;

    private static final int PORT_MIN = 1;
    private static final int PORT_MAX = 65_535;

    private static final int KEEPALIVE_OFF = 0;
    private static final int KEEPALIVE_MIN = 5;
    private static final int KEEPALIVE_MAX = 65_535;

    private static final int REASON_MIN = 0;
    private static final int REASON_MAX = 255;


    private static final int SIBSCRIPTION_ID_MIN = 1;
    private static final int SIBSCRIPTION_ID_MAX = 268_435_455;

    private static final int RETAIN_HANDLING_MIN = 0;
    private static final int RETAIN_HANDLING_MAX = 2;

    @SuppressWarnings("PMD.UnusedPrivateField") // TODO: remove after use client
    private final GRPCDiscoveryClient client;
    private final Server server;
    private final int boundPort;
    private MqttLib mqttLib;
    private String shutdownReason;


    /**
     * MQTT client control service implementation.
     */
    class MqttClientControlImpl extends MqttClientControlGrpc.MqttClientControlImplBase {

        @Override
        public void shutdownAgent(ShutdownRequest request, StreamObserver<Empty> responseObserver) {
            // save reason
            shutdownReason = request.getReason();

            // log an event
            logger.atInfo().log("shutdownAgent: {}", shutdownReason);

            Empty reply = Empty.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();

            // request server to shutdown
            server.shutdown();
        }

        @Override
        public void createMqttConnection(MqttConnectRequest request,
                                            StreamObserver<MqttConnectionId> responseObserver) {

            String clientId = request.getClientId();
            String host = request.getHost();
            int port = request.getPort();

            logger.atInfo().log("createMqttConnection: clientId {} broker {}:{}", clientId, host, port);

            if (clientId == null || clientId.isEmpty()) {
                logger.atWarn().log("clientId can't be empty");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("clientId can't be empty")
                                            .asRuntimeException());
                return;
            }

            if (host == null || host.isEmpty()) {
                logger.atWarn().log("host can't be empty");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("host can't be empty")
                                            .asRuntimeException());
                return;
            }

            if (port < PORT_MIN || port > PORT_MAX) {
                logger.atWarn().log("invalid port, must be in range [{}, {}]", PORT_MIN, PORT_MAX);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid port, must be in range [1, 65535]")
                                            .asRuntimeException());
                return;
            }

            MqttProtoVersion version = request.getProtocolVersion();
            if (version != MqttProtoVersion.MQTT_PROTOCOL_V50) {
                logger.atWarn().log("MQTT_PROTOCOL_V50 is only supported but {} requested", version);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                .withDescription("invalid protocolVersion, only MQTT_PROTOCOL_V50 is supported")
                                .asRuntimeException());
                return;
            }

            int keepalive = request.getKeepalive();
            if (keepalive != KEEPALIVE_OFF && (keepalive < KEEPALIVE_MIN || keepalive > KEEPALIVE_MAX)) {
                logger.atWarn().log("invalid keepalive, must be in range [{}, {}]", KEEPALIVE_MIN, KEEPALIVE_MAX);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid keepalive, must be in range [1, 65535]")
                                            .asRuntimeException());
                return;
            }


            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid connect timeout, must be >= {} but {}", TIMEOUT_MIN, timeout);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid connect timeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }

            MqttLib.ConnectionParams.ConnectionParamsBuilder builder = MqttLib.ConnectionParams.builder()
                            .clientId(clientId)
                            .host(host)
                            .port(port)
                            .keepalive(keepalive)
                            .cleanSession(request.getCleanSession())
                            .timeout(timeout);

            // check TLS optional settings
            if (request.hasTls()) {
                TLSSettings tls = request.getTls();
                String ca = tls.getCa();

                if (ca == null || ca.isEmpty()) {
                    logger.atWarn().log("ca is empty");
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("CA is empty")
                                                .asRuntimeException());
                    return;
                }

                String cert = tls.getCert();
                if (cert == null || cert.isEmpty()) {
                    logger.atWarn().log("cert is empty");
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("cert is empty")
                                                .asRuntimeException());
                    return;
                }

                String key = tls.getKey();
                if (key == null || key.isEmpty()) {
                    logger.atWarn().log("key is empty");
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("key is empty")
                                                .asRuntimeException());
                    return;
                }

                builder.ca(ca).cert(cert).key(key);
            }


            int connectionId;
            try {
                MqttConnection connection = mqttLib.createConnection(builder.build());
                connectionId = mqttLib.registerConnection(connection);
            } catch (MqttException ex) {
                logger.atWarn().withThrowable(ex).log("Exception during connect");
                responseObserver.onError(ex);
                return;
            }

            MqttConnectionId reply = MqttConnectionId.newBuilder().setConnectionId(connectionId).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void closeMqttConnection(MqttCloseRequest request, StreamObserver<Empty> responseObserver) {

            int connectionId = request.getConnectionId().getConnectionId();
            int reason = request.getReason();
            logger.atInfo().log("closeMqttConnection: connectionId {} reason {}", connectionId, reason);

            if (reason < REASON_MIN || reason > REASON_MAX) {
                logger.atWarn().log("invalid reason, must be in range [{}, {}]", REASON_MIN, REASON_MAX);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid reason, must be in range [0, 255]")
                                            .asRuntimeException());
                return;
            }

            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid disconnect timeout, must be >= {} but {}", TIMEOUT_MIN, timeout);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid disconnect timeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }

            MqttConnection connection = mqttLib.unregisterConnection(connectionId);
            if (connection == null) {
                logger.atWarn().log(CONNECTION_WITH_DOES_NOT_FOUND, connectionId);
                responseObserver.onError(Status.NOT_FOUND
                                            .withDescription(CONNECTION_DOES_NOT_FOUND)
                                            .asRuntimeException());
                return;
            }

            try {
                // TODO: pass also DISCONNECT properties
                connection.disconnect(reason, timeout);
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("exception during disconnect");
                responseObserver.onError(ex);
                return;
            }

            Empty reply = Empty.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void publishMqtt(MqttPublishRequest request, StreamObserver<MqttPublishReply> responseObserver) {
            Mqtt5Message message = request.getMsg();

            logger.atDebug().log("publishMqtt");

            int qos = message.getQosValue();
            if (qos < QOS_MIN || qos > QOS_MAX) {
                logger.atWarn().log("qos is invalid can be in range [0,3] but is {}", qos);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("qos is invalid can be in range [0,3]")
                                            .asRuntimeException());
                return;
            }

            String topic = message.getTopic();
            if (topic == null || topic.isEmpty()) {
                logger.atWarn().log("topic can't be empty");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("topic can't be empty")
                                            .asRuntimeException());
                return;
            }

            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid publish timeout, must be >= {} but {}", TIMEOUT_MIN, timeout);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid publish timeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }

            int connectionId = request.getConnectionId().getConnectionId();
            MqttConnection connection = mqttLib.getConnection(connectionId);
            if (connection == null) {
                logger.atWarn().log(CONNECTION_WITH_DOES_NOT_FOUND, connectionId);
                responseObserver.onError(Status.NOT_FOUND
                                            .withDescription(CONNECTION_DOES_NOT_FOUND)
                                            .asRuntimeException());
                return;
            }

            boolean isRetain = message.getRetain();
            logger.atInfo().log("Publish: connectionId {} topic {} QOS {} retain {}",
                                    connectionId, topic, qos, isRetain);

            MqttPublishReply.Builder builder = MqttPublishReply.newBuilder();
            try {
                // TODO: pass also user's properties
                MqttConnection.PubAckInfo pubAckInfo = connection.publish(isRetain, qos, timeout, topic,
                                                            message.getPayload().toByteArray());
                if (pubAckInfo != null) {
                    int reasonCode = pubAckInfo.getReasonCode();
                    builder.setReasonCode(reasonCode);
                    String reasonString = pubAckInfo.getReasonString();
                    if (reasonString != null) {
                        builder.setReasonString(reasonString);
                    }
                    logger.atInfo().log("Publish response: reason code {} reason string {}", reasonCode, reasonString);
                }
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("exception during publish");
                responseObserver.onError(ex);
                return;
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void subscribeMqtt(MqttSubscribeRequest request, StreamObserver<MqttSubscribeReply> responseObserver) {

            logger.atDebug().log("subscribeMqtt");

            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid subscribe timeout, must be >= {} but {}", TIMEOUT_MIN, timeout);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid subscribe timeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }

            Integer subscriptionId = null;
            if (request.hasSubscriptionId()) {
                subscriptionId = request.getSubscriptionId();
                if (subscriptionId < SIBSCRIPTION_ID_MIN || subscriptionId > SIBSCRIPTION_ID_MAX) {
                    logger.atWarn().log("invalid subscription id, must be >= {} <= {} but {}", SIBSCRIPTION_ID_MIN,
                                            SIBSCRIPTION_ID_MAX,
                                            subscriptionId);
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                    .withDescription("invalid subscription id, must be >= 1 and <= 268435455")
                                    .asRuntimeException());
                    return;
                }
            }

            List<Mqtt5Subscription> subscriptions = request.getSubscriptionsList();
            if (subscriptions.isEmpty()) {
                logger.atWarn().log("empty subscriptions list");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("empty subscriptions list")
                                            .asRuntimeException());
                return;
            }

            List<MqttConnection.Subscription> outSubscriptions = new ArrayList<>();
            int index = 0;
            for (Mqtt5Subscription subscription : subscriptions) {
                String filter = subscription.getFilter();
                if (filter == null || filter.isEmpty()) {
                    logger.atWarn().log("filter can't be empty but missing at index {}", index);
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("filter can't be empty")
                                                .asRuntimeException());
                    return;
                }

                int qos = subscription.getQosValue();
                if (qos < QOS_MIN || qos > QOS_MAX) {
                    logger.atWarn().log("qos is invalid can be in range [0,3] but is {} at index {}", qos, index);
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("qos is invalid can be in range [0,3]")
                                                .asRuntimeException());
                    return;
                }

                int retainHandling = subscription.getRetainHandlingValue();
                if (retainHandling < RETAIN_HANDLING_MIN || retainHandling > RETAIN_HANDLING_MAX) {
                    logger.atWarn().log("qos is invalid can be in range [0,3] but is {} at index {}", qos, index);
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("qos is invalid can be in range [0,3]")
                                                .asRuntimeException());
                    return;
                }

                boolean noLocal = subscription.getNoLocal();
                boolean retainAsLocal = subscription.getRetainAsPublished();
                MqttConnection.Subscription tmp = new MqttConnection.Subscription(filter, qos, noLocal, retainAsLocal,
                                                                                    retainHandling);
                logger.atInfo().log("Subscription: filter {} QoS {} noLocal {} retainAsLocal {} retainHandling {}",
                                        filter, qos, noLocal, retainAsLocal, retainHandling);
                outSubscriptions.add(tmp);
                index++;
            }

            int connectionId = request.getConnectionId().getConnectionId();
            MqttConnection connection = mqttLib.getConnection(connectionId);
            if (connection == null) {
                logger.atWarn().log(CONNECTION_WITH_DOES_NOT_FOUND, connectionId);
                responseObserver.onError(Status.NOT_FOUND
                                            .withDescription(CONNECTION_DOES_NOT_FOUND)
                                            .asRuntimeException());
                return;
            }

            logger.atInfo().log("Subscribe: connectionId {} subscriptionId {} for {} filters",
                                    connectionId, subscriptionId, outSubscriptions.size());
            MqttSubscribeReply.Builder builder = MqttSubscribeReply.newBuilder();
            try {
                // TODO: pass also user's properties
                MqttConnection.SubAckInfo subAckInfo = connection.subscribe(timeout, subscriptionId, outSubscriptions);
                if (subAckInfo != null) {
                    List<Integer> codes = subAckInfo.getReasonCodes();
                    if (codes != null) {
                        builder.addAllReasonCodes(codes);
                    }

                    String reasonString = subAckInfo.getReasonString();
                    if (reasonString != null) {
                         builder.setReasonString(reasonString);
                    }
                    logger.atInfo().log("Subscribe response: reason codes {} reason string {}", codes, reasonString);
                }
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("exception during publish");
                responseObserver.onError(ex);
                return;
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void unsubscribeMqtt(MqttUnsubscribeRequest request,
                                        StreamObserver<MqttSubscribeReply> responseObserver) {

            logger.atDebug().log("unsubscribeMqtt");

            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid unsubscribe timeout, must be >= {} but {}", TIMEOUT_MIN, timeout);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid unsubscribe timeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }

            List<String> filters = request.getFiltersList();
            if (filters.isEmpty()) {
                logger.atWarn().log("empty filters list");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("empty filters list")
                                            .asRuntimeException());
                return;
            }

            int connectionId = request.getConnectionId().getConnectionId();
            MqttConnection connection = mqttLib.getConnection(connectionId);
            if (connection == null) {
                logger.atWarn().log(CONNECTION_WITH_DOES_NOT_FOUND, connectionId);
                responseObserver.onError(Status.NOT_FOUND
                                            .withDescription(CONNECTION_DOES_NOT_FOUND)
                                            .asRuntimeException());
                return;
            }

            logger.atInfo().log("Unsubscribe: connectionId {} for {} filters",
                                    connectionId, filters);
            MqttSubscribeReply.Builder builder = MqttSubscribeReply.newBuilder();
            try {
                // TODO: pass also user's properties
                MqttConnection.SubAckInfo subAckInfo = connection.unsubscribe(timeout, filters);
                if (subAckInfo != null) {
                    List<Integer> codes = subAckInfo.getReasonCodes();
                    if (codes != null) {
                        builder.addAllReasonCodes(codes);
                    }

                    String reasonString = subAckInfo.getReasonString();
                    if (reasonString != null) {
                         builder.setReasonString(reasonString);
                    }
                    logger.atInfo().log("Unsubscribe response: reason codes {} reason string {}", codes, reasonString);
                }
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("exception during publish");
                responseObserver.onError(ex);
                return;
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }

    /**
     * Create instance of gRPC server.
     *
     * @param client reference to gRPC client
     * @param host bind address
     * @param port bind port, or 0 to autoselect
     * @throws IOException on errors
     */
    public GRPCControlServer(GRPCDiscoveryClient client, String host, int port) throws IOException {
        super();
        this.client = client;

        // TODO: Java implementation of gRPC server has no usage of host
        server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                    .addService(new MqttClientControlImpl())
                    .build()
                    .start();

        this.boundPort = server.getPort();
        logger.atInfo().log("GRPCControlServer created and listed on {}:{}", host, boundPort);
    }

    public int getPort() {
        return boundPort;
    }

    public String getShutdownReason() {
        return shutdownReason;
    }


    public void waiting(MqttLib mqttLib) throws InterruptedException {
        this.mqttLib = mqttLib;
        logger.atInfo().log("Server awaitTermination");
        server.awaitTermination();
        logger.atInfo().log("Server awaitTermination done");
    }

    public void close() {
        server.shutdown(); // or       shutdownNow()
    }
}
