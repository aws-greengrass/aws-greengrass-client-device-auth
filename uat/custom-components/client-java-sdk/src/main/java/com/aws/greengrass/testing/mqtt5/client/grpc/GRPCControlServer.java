/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt.client.CoreDeviceDiscoverReply;
import com.aws.greengrass.testing.mqtt.client.CoreDeviceDiscoverRequest;
import com.aws.greengrass.testing.mqtt.client.Empty;
import com.aws.greengrass.testing.mqtt.client.Mqtt5ConnAck;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Message;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Properties;
import com.aws.greengrass.testing.mqtt.client.Mqtt5Subscription;
import com.aws.greengrass.testing.mqtt.client.MqttClientControlGrpc;
import com.aws.greengrass.testing.mqtt.client.MqttCloseRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectReply;
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
import com.aws.greengrass.testing.mqtt5.client.DiscoverClient;
import com.aws.greengrass.testing.mqtt5.client.GRPCClient;
import com.aws.greengrass.testing.mqtt5.client.MqttConnection;
import com.aws.greengrass.testing.mqtt5.client.MqttLib;
import com.aws.greengrass.testing.mqtt5.client.exceptions.DiscoverException;
import com.aws.greengrass.testing.mqtt5.client.exceptions.MqttException;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of gRPC server handles requests of OTF.
 */
class GRPCControlServer {
    private static final Logger logger = LogManager.getLogger(GRPCControlServer.class);

    private static final String CONNECTION_WITH_DOES_NOT_FOUND = "connection with id {} doesn't found";
    private static final String CONNECTION_DOES_NOT_FOUND = "connection doesn't found";
    private static final String EMPTY_CERTIFICATE = "empty certificate";
    private static final String EMPTY_PRIVATE_KEY = "empty private key";

    private static final int TIMEOUT_MIN = 1;

    private static final int QOS_MIN = 0;
    private static final int QOS_MAX = 2;

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

    private final GRPCClient client;
    private final Server server;
    private final int boundPort;

    private MqttLib mqttLib;
    private DiscoverClient discoverClient;
    private String shutdownReason;


    /**
     * MQTT client control service implementation.
     */
    class MqttClientControlImpl extends MqttClientControlGrpc.MqttClientControlImplBase {

        /**
         * Handler of ShutdownAgent gRPC call.
         *
         * @param request incoming request
         * @param responseObserver response control
         */
        @Override
        public void shutdownAgent(ShutdownRequest request, StreamObserver<Empty> responseObserver) {
            // save reason
            shutdownReason = request.getReason();

            // log an event
            logger.atInfo().log("shutdownAgent: reason {}", shutdownReason);

            Empty reply = Empty.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();

            // request server to shutdown
            server.shutdown();
        }

        /**
         * Handler of CreateMqttConnection gRPC call.
         *
         * @param request incoming request
         * @param responseObserver response control
         */
        @SuppressWarnings("PMD.CognitiveComplexity")
        @Override
        public void createMqttConnection(MqttConnectRequest request,
                                            StreamObserver<MqttConnectReply> responseObserver) {

            String clientId = request.getClientId();
            if (clientId == null || clientId.isEmpty()) {
                logger.atWarn().log("empty clientId");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("empty clientId")
                                            .asRuntimeException());
                return;
            }

            String host = request.getHost();
            if (host == null || host.isEmpty()) {
                logger.atWarn().log("empty host");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("empty host")
                                            .asRuntimeException());
                return;
            }

            int port = request.getPort();
            if (port < PORT_MIN || port > PORT_MAX) {
                logger.atWarn().log("invalid port {}, must be in range [{}, {}]", port, PORT_MIN, PORT_MAX);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid port, must be in range [1, 65535]")
                                            .asRuntimeException());
                return;
            }

            MqttProtoVersion version = request.getProtocolVersion();
            if (version != MqttProtoVersion.MQTT_PROTOCOL_V_311 && version != MqttProtoVersion.MQTT_PROTOCOL_V_50) {
                logger.atWarn().log("invalid protocolVersion {}, {} and {} are only supported",
                                        version,
                                        MqttProtoVersion.MQTT_PROTOCOL_V_311,
                                        MqttProtoVersion.MQTT_PROTOCOL_V_50);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                .withDescription("invalid protocolVersion, only "
                                                    + "MQTT_PROTOCOL_V_311 and MQTT_PROTOCOL_V_50 are supported")
                                .asRuntimeException());
                return;
            }

            int keepalive = request.getKeepalive();
            if (keepalive != KEEPALIVE_OFF && (keepalive < KEEPALIVE_MIN || keepalive > KEEPALIVE_MAX)) {
                logger.atWarn().log("invalDid keepalive {}, must be in range [{}, {}]",
                                        keepalive, KEEPALIVE_MIN, KEEPALIVE_MAX);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid keepalive, must be in range [1, 65535]")
                                            .asRuntimeException());
                return;
            }

            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid connect timeout {} must be >= {}", timeout, TIMEOUT_MIN);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid connect timeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }

            MqttLib.ConnectionParams.ConnectionParamsBuilder connectionParamsBuilder
                    = MqttLib.ConnectionParams.builder()
                            .clientId(clientId)
                            .host(host)
                            .port(port)
                            .keepalive(keepalive)
                            .cleanSession(request.getCleanSession())
                            .mqtt50(version == MqttProtoVersion.MQTT_PROTOCOL_V_50);

            // check TLS optional settings
            if (request.hasTls()) {
                TLSSettings tls = request.getTls();
                List<String> caList = tls.getCaListList();

                if (caList == null || caList.isEmpty()) {
                    logger.atWarn().log("empty CA list");
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("empty CA list")
                                                .asRuntimeException());
                    return;
                }

                String cert = tls.getCert();
                if (cert == null || cert.isEmpty()) {
                    logger.atWarn().log(EMPTY_CERTIFICATE);
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription(EMPTY_CERTIFICATE)
                                                .asRuntimeException());
                    return;
                }

                String key = tls.getKey();
                if (key == null || key.isEmpty()) {
                    logger.atWarn().log(EMPTY_PRIVATE_KEY);
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription(EMPTY_PRIVATE_KEY)
                                                .asRuntimeException());
                    return;
                }

                final String ca = String.join("\n", caList);
                connectionParamsBuilder.ca(ca).cert(cert).key(key);
            }

            if (!request.getPropertiesList().isEmpty()) {
                connectionParamsBuilder.userProperties(request.getPropertiesList());
            }

            if (request.hasRequestResponseInformation()) {
                connectionParamsBuilder.requestResponseInformation(request.getRequestResponseInformation());
            }

            logger.atInfo().log("createMqttConnection: clientId {} broker {}:{}", clientId, host, port);
            MqttConnectReply.Builder builder = MqttConnectReply.newBuilder();

            AtomicReference<Integer> connectionId = new AtomicReference<>();
            try {
                MqttConnection connection = mqttLib.createConnection(connectionParamsBuilder.build(), client);
                connectionId.set(mqttLib.registerConnection(connection));

                MqttConnection.ConnectResult connectResult = connection.start(timeout, connectionId.get());
                if (connectResult != null) {
                    builder.setConnectionId(MqttConnectionId.newBuilder().setConnectionId(connectionId.get()).build());
                    convertConnectResult(connectResult, builder);
                    connectionId.set(null);
                }
            } catch (MqttException ex) {
                logger.atWarn().withThrowable(ex).log("Exception during connect");
                responseObserver.onError(ex);
                return;
            } finally {
                Integer id = connectionId.getAndSet(null);
                if (id != null) {
                    mqttLib.unregisterConnection(id);
                }
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }

        /**
         * Handler of CloseMqttConnection gRPC call.
         *
         * @param request incoming request
         * @param responseObserver response control
         */
        @Override
        public void closeMqttConnection(MqttCloseRequest request, StreamObserver<Empty> responseObserver) {

            int reason = request.getReason();
            if (reason < REASON_MIN || reason > REASON_MAX) {
                logger.atWarn().log("invalid reason {}, must be in range [{}, {}]", reason, REASON_MIN, REASON_MAX);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid reason, must be in range [0, 255]")
                                            .asRuntimeException());
                return;
            }

            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid disconnect timeout, must be >= {}", timeout, TIMEOUT_MIN);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid disconnect timeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }

            List<Mqtt5Properties> userProperties = new ArrayList<>();
            if (!request.getPropertiesList().isEmpty()) {
                userProperties = request.getPropertiesList();
            }

            int connectionId = request.getConnectionId().getConnectionId();
            MqttConnection connection = mqttLib.unregisterConnection(connectionId);
            if (connection == null) {
                logger.atWarn().log(CONNECTION_WITH_DOES_NOT_FOUND, connectionId);
                responseObserver.onError(Status.NOT_FOUND
                                            .withDescription(CONNECTION_DOES_NOT_FOUND)
                                            .asRuntimeException());
                return;
            }

            logger.atInfo().log("closeMqttConnection: connectionId {} reason {}", connectionId, reason);
            try {
                connection.disconnect(timeout, reason, userProperties);
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("exception during disconnect");
                responseObserver.onError(ex);
                return;
            }

            Empty reply = Empty.newBuilder().build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        /**
         * Handler of PublishMqtt gRPC call.
         *
         * @param request incoming request
         * @param responseObserver response control
         */
        @Override
        public void publishMqtt(MqttPublishRequest request, StreamObserver<MqttPublishReply> responseObserver) {

            Mqtt5Message message = request.getMsg();

            int qos = message.getQosValue();
            if (qos < QOS_MIN || qos > QOS_MAX) {
                logger.atWarn().log("invalid QoS {}, must be in range [{},{}]", qos, QOS_MIN, QOS_MAX);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid QoS, must be in range [0,2]")
                                            .asRuntimeException());
                return;
            }

            String topic = message.getTopic();
            if (topic == null || topic.isEmpty()) {
                logger.atWarn().log("empty topic");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("empty topic")
                                            .asRuntimeException());
                return;
            }

            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid publish timeout {}, must be >= {}", timeout, TIMEOUT_MIN);
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
            logger.atInfo().log("Publish: connectionId {} topic {} QoS {} retain {}",
                                    connectionId, topic, qos, isRetain);

            MqttConnection.Message.MessageBuilder internalBuilder = MqttConnection.Message.builder()
                                .qos(qos)
                                .retain(isRetain)
                                .topic(topic)
                                .payload(message.getPayload().toByteArray());

            // please use the same order as in 3.3.2 PUBLISH Variable Header of MQTT v5.0 specification
            if (message.hasPayloadFormatIndicator()) {
                internalBuilder.payloadFormatIndicator(message.getPayloadFormatIndicator());
            }

            if (message.hasMessageExpiryInterval()) {
                internalBuilder.messageExpiryInterval(message.getMessageExpiryInterval());
            }

            if (message.hasResponseTopic()) {
                internalBuilder.responseTopic(message.getResponseTopic());
            }

            if (message.hasCorrelationData()) {
                internalBuilder.correlationData(message.getCorrelationData().toByteArray());
            }

            if (!message.getPropertiesList().isEmpty()) {
                internalBuilder.userProperties(message.getPropertiesList());
            }

            if (message.hasContentType()) {
                internalBuilder.contentType(message.getContentType());
            }

            MqttPublishReply.Builder builder = MqttPublishReply.newBuilder();
            try {
                MqttConnection.PubAckInfo pubAckInfo = connection.publish(timeout, internalBuilder.build());
                convertPubAckInfo(pubAckInfo, builder);
                if (pubAckInfo != null) {
                    logger.atInfo().log("Publish response: connectionId {} reason code {} reason string {}",
                                            connectionId, pubAckInfo.getReasonCode(), pubAckInfo.getReasonString());
                }
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("exception during publish");
                responseObserver.onError(ex);
                return;
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }


        /**
         * Handler of SubscribeMqtt gRPC call.
         *
         * @param request incoming request
         * @param responseObserver response control
         */
        @SuppressWarnings("PMD.CognitiveComplexity")
        @Override
        public void subscribeMqtt(MqttSubscribeRequest request, StreamObserver<MqttSubscribeReply> responseObserver) {

            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid subscribe timeout {}, must be >= {}", timeout, TIMEOUT_MIN);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid subscribe timeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }

            Integer subscriptionId = null;
            if (request.hasSubscriptionId()) {
                subscriptionId = request.getSubscriptionId();
                if (subscriptionId < SIBSCRIPTION_ID_MIN || subscriptionId > SIBSCRIPTION_ID_MAX) {
                    logger.atWarn().log("invalid subscription id {} must be >= {} and <= {}", subscriptionId,
                                            SIBSCRIPTION_ID_MIN, SIBSCRIPTION_ID_MAX);
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
                    logger.atWarn().log("empty filter at subscription index {}", index);
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("empty filter")
                                                .asRuntimeException());
                    return;
                }

                int qos = subscription.getQosValue();
                if (qos < QOS_MIN || qos > QOS_MAX) {
                    logger.atWarn().log("invalid QoS {} at subscription index {}, must be in range [{},{}]",
                                            qos, index, QOS_MIN, QOS_MAX);
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("invalid QoS, must be in range [0,2]")
                                                .asRuntimeException());
                    return;
                }

                int retainHandling = subscription.getRetainHandlingValue();
                if (retainHandling < RETAIN_HANDLING_MIN || retainHandling > RETAIN_HANDLING_MAX) {
                    logger.atWarn().log("invalid retainHandling {} at subscription index {}, must be in range [{},{}]",
                                            qos, index, RETAIN_HANDLING_MIN, RETAIN_HANDLING_MAX);
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("invalid retainHandling, must be in range [0,2]")
                                                .asRuntimeException());
                    return;
                }

                boolean noLocal = subscription.getNoLocal();
                boolean retainAsPublished = subscription.getRetainAsPublished();
                MqttConnection.Subscription tmp = new MqttConnection.Subscription(filter, qos, noLocal,
                                                                                    retainAsPublished, retainHandling);
                logger.atInfo().log("Subscription: filter {} QoS {} noLocal {} retainAsPublished {} retainHandling {}",
                                        filter, qos, noLocal, retainAsPublished, retainHandling);
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
                MqttConnection.SubAckInfo subAckInfo = connection.subscribe(timeout,
                        subscriptionId, request.getPropertiesList(), outSubscriptions);
                convertSubAckInfo(subAckInfo, builder);

                if (subAckInfo != null) {
                    logger.atInfo().log("Subscribe response: connectionId {} reason codes {} reason string {}",
                                        connectionId, subAckInfo.getReasonCodes(), subAckInfo.getReasonString());
                }
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("exception during subscribe");
                responseObserver.onError(ex);
                return;
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }

        /**
         * Handler of UnsubscribeMqtt gRPC call.
         *
         * @param request incoming request
         * @param responseObserver response control
         */
        @Override
        public void unsubscribeMqtt(MqttUnsubscribeRequest request,
                                        StreamObserver<MqttSubscribeReply> responseObserver) {

            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid unsubscribe timeout {}, must be >= {}", timeout, TIMEOUT_MIN);
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
                MqttConnection.UnsubAckInfo unsubAckInfo = connection.unsubscribe(timeout,
                        request.getPropertiesList(), filters);
                convertSubAckInfo(unsubAckInfo, builder);
                if (unsubAckInfo != null) {
                    logger.atInfo().log("Unsubscribe response: connectionId {} reason codes {} reason string {}",
                                        connectionId, unsubAckInfo.getReasonCodes(), unsubAckInfo.getReasonString());
                }
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("exception during unsubscribe");
                responseObserver.onError(ex);
                return;
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }

        /**
         * Handler of DiscoverCoreDevice gRPC call.
         *
         * @param request incoming request
         * @param responseObserver response control
         */
        @Override
        public void discoverCoreDevice(CoreDeviceDiscoverRequest request,
                                            StreamObserver<CoreDeviceDiscoverReply> responseObserver) {
            int timeout = request.getTimeout();
            if (timeout < TIMEOUT_MIN) {
                logger.atWarn().log("invalid unsubscribe timeout {}, must be >= {}", timeout, TIMEOUT_MIN);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid unsubscribe timeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }

            final String ca = request.getCa();
            if (ca == null || ca.isEmpty()) {
                logger.atWarn().log("empty CA");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("empty CA")
                                            .asRuntimeException());
                return;
            }

            final String cert = request.getCert();
            if (cert == null || cert.isEmpty()) {
                logger.atWarn().log(EMPTY_CERTIFICATE);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription(EMPTY_CERTIFICATE)
                                            .asRuntimeException());
                return;
            }

            final String key = request.getKey();
            if (key == null || key.isEmpty()) {
                logger.atWarn().log(EMPTY_PRIVATE_KEY);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription(EMPTY_PRIVATE_KEY)
                                            .asRuntimeException());
                return;
            }

            final String thingName = request.getThingName();
            if (thingName == null || thingName.isEmpty()) {
                logger.atWarn().log("empty thing name");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("empty thing name")
                                            .asRuntimeException());
                return;
            }

            final String region = request.getRegion();
            if (region == null || region.isEmpty()) {
                logger.atWarn().log("empty region");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("empty region")
                                            .asRuntimeException());
                return;
            }

            CoreDeviceDiscoverReply reply;
            try {
                reply = discoverClient.discoverCoreDevice(request);
            } catch (DiscoverException ex) {
                logger.atError().withThrowable(ex).log("exception during discover");
                responseObserver.onError(ex);
                return;
            }

            responseObserver.onNext(reply);
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
    public GRPCControlServer(@NonNull GRPCClient client, @NonNull String host, int port) throws IOException {
        super();
        this.client = client;

        // TODO: Java implementation of gRPC server has no usage of host
        this.server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                    .addService(new MqttClientControlImpl())
                    .build()
                    .start();

        this.boundPort = server.getPort();
        logger.atInfo().log("GRPCControlServer created and listed on {}:{}", host, boundPort);
    }

    /**
     * Gets actual port where gRPC server is bound.
     *
     * @return actual bound port
     */
    public int getPort() {
        return boundPort;
    }

    /**
     * Returns shutdown reason if has been shutdown by party requests.
     *
     * @return reason of shutdown or null when shutdown was not initiated by party
     */
    public String getShutdownReason() {
        return shutdownReason;
    }

    /**
     * Handle gRPC requests and wait to server shutdown.
     *
     * @param mqttLib reference to MQTT side of the client to handler incoming requests
     */
    public void waiting(MqttLib mqttLib, DiscoverClient discoverClient) throws InterruptedException {
        this.mqttLib = mqttLib;
        this.discoverClient = discoverClient;
        logger.atInfo().log("Server awaitTermination");
        server.awaitTermination();
        logger.atInfo().log("Server awaitTermination done");
    }

    /**
     * Closes the gRPC server.
     */
    public void close() {
        server.shutdown(); // or shutdownNow() ?
    }


    private static void convertConnectResult(MqttConnection.ConnectResult connectResult,
                                                MqttConnectReply.Builder builder) {
        builder.setConnected(connectResult.isConnected());

        String error = connectResult.getError();
        if (error != null) {
            builder.setError(error);
        }

        MqttConnection.ConnAckInfo connAckInfo = connectResult.getConnAckInfo();
        if (connAckInfo != null) {
            builder.setConnAck(convertConnAckInfo(connAckInfo));
        }
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    private static Mqtt5ConnAck convertConnAckInfo(MqttConnection.ConnAckInfo connAckInfo) {
        final Mqtt5ConnAck.Builder builder = Mqtt5ConnAck.newBuilder();

        // please keep order the same as in 3.2.2 CONNACK Variable Header of MQTT v5.0 spec
        Boolean sessionPresent = connAckInfo.getSessionPresent();
        if (sessionPresent != null) {
            builder.setSessionPresent(sessionPresent);
        }

        Integer reasonCode = connAckInfo.getReasonCode();
        if (reasonCode != null) {
            builder.setReasonCode(reasonCode);
        }

        // please keep order the same as in 3.2.2.3 CONNACK Properties of MQTT v5.0 spec
        Integer sessionExpiryInterval = connAckInfo.getSessionExpiryInterval();
        if (sessionExpiryInterval != null) {
            builder.setSessionExpiryInterval(sessionExpiryInterval);
        }

        Integer receiveMaximum = connAckInfo.getReceiveMaximum();
        if (receiveMaximum != null) {
            builder.setReceiveMaximum(receiveMaximum);
        }

        Integer maximumQoS = connAckInfo.getMaximumQoS();
        if (maximumQoS != null) {
            builder.setMaximumQoS(maximumQoS);
        }

        Boolean retainAvailable = connAckInfo.getRetainAvailable();
        if (retainAvailable != null) {
            builder.setRetainAvailable(retainAvailable);
        }

        Integer maximumPacketSize = connAckInfo.getMaximumPacketSize();
        if (maximumPacketSize != null) {
            builder.setMaximumPacketSize(maximumPacketSize);
        }

        String assignedClientId = connAckInfo.getAssignedClientId();
        if (assignedClientId != null) {
            builder.setAssignedClientId(assignedClientId);
        }

        Integer topicAliasMaximum = connAckInfo.getTopicAliasMaximum();
        if (topicAliasMaximum != null) {
            builder.setTopicAliasMaximum(topicAliasMaximum);
        }

        String reasonString = connAckInfo.getReasonString();
        if (reasonString != null) {
            builder.setReasonString(reasonString);
        }

        List<Mqtt5Properties> userProperties = connAckInfo.getUserProperties();
        if (userProperties != null) {
            builder.addAllProperties(userProperties);
        }

        Boolean wildcardSubscriptionsAvailable = connAckInfo.getWildcardSubscriptionsAvailable();
        if (wildcardSubscriptionsAvailable != null) {
            builder.setWildcardSubscriptionsAvailable(wildcardSubscriptionsAvailable);
        }

        Boolean subscriptionIdentifiersAvailable = connAckInfo.getSubscriptionIdentifiersAvailable();
        if (subscriptionIdentifiersAvailable != null) {
            builder.setSubscriptionIdentifiersAvailable(subscriptionIdentifiersAvailable);
        }

        Boolean sharedSubscriptionsAvailable = connAckInfo.getSharedSubscriptionsAvailable();
        if (sharedSubscriptionsAvailable != null) {
            builder.setSharedSubscriptionsAvailable(sharedSubscriptionsAvailable);
        }

        Integer serverKeepAlive = connAckInfo.getServerKeepAlive();
        if (serverKeepAlive != null) {
            builder.setServerKeepAlive(serverKeepAlive);
        }

        String responseInformation = connAckInfo.getResponseInformation();
        if (responseInformation != null) {
            builder.setResponseInformation(responseInformation);
        }

        String serverReference = connAckInfo.getServerReference();
        if (serverReference != null) {
            builder.setServerReference(serverReference);
        }

        return builder.build();
    }

    private static void convertSubAckInfo(MqttConnection.SubAckInfo subAckInfo, MqttSubscribeReply.Builder builder) {
        if (subAckInfo == null) {
            return;
        }

        List<Integer> codes = subAckInfo.getReasonCodes();
        if (codes != null) {
            builder.addAllReasonCodes(codes);
        }

        String reasonString = subAckInfo.getReasonString();
        if (reasonString != null) {
            builder.setReasonString(reasonString);
        }

        List<Mqtt5Properties> userProperties = subAckInfo.getUserProperties();
        if (userProperties != null) {
            builder.addAllProperties(userProperties);
        }
    }

    private static void convertPubAckInfo(MqttConnection.PubAckInfo pubAckInfo, MqttPublishReply.Builder builder) {
        if (pubAckInfo == null) {
            return;
        }

        Integer reasonCode = pubAckInfo.getReasonCode();
        if (reasonCode != null) {
            builder.setReasonCode(reasonCode);
        }

        String reasonString = pubAckInfo.getReasonString();
        if (reasonString != null) {
            builder.setReasonString(reasonString);
        }

        List<Mqtt5Properties> userProperties = pubAckInfo.getUserProperties();
        if (userProperties != null) {
            builder.addAllProperties(userProperties);
        }
    }
}
