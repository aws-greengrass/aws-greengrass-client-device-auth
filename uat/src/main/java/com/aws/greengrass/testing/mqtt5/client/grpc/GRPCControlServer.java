/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aws.greengrass.testing.mqtt5.client.grpc;

import com.aws.greengrass.testing.mqtt.client.MqttClientControlGrpc;
import com.aws.greengrass.testing.mqtt.client.MqttCloseRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectRequest;
import com.aws.greengrass.testing.mqtt.client.MqttConnectionId;
import com.aws.greengrass.testing.mqtt.client.MqttProtoVersion;
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

/**
 * Implementation of gRPC server handles requests of OTF.
 */
class GRPCControlServer {
    private static final Logger logger = LogManager.getLogger(GRPCControlServer.class);

    private static final int PORT_MIN = 1;
    private static final int PORT_MAX = 65_535;

    private static final int KEEPALIVE_OFF = 0;
    private static final int KEEPALIVE_MIN = 5;
    private static final int KEEPALIVE_MAX = 65_535;

    private static final int REASON_MIN = 0;
    private static final int REASON_MAX = 255;

    private static final int CONNECT_TIMEOUT_MIN = 1;



    @SuppressWarnings("PMD.UnusedPrivateField") // TODO: remove
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


            int connectTimeout = request.getConnectTimeout();
            if (connectTimeout < CONNECT_TIMEOUT_MIN) {
                logger.atWarn().log("invalid connectTimeout, must be >= {}", CONNECT_TIMEOUT_MIN);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid connectTimeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }

            MqttLib.ConnectionParams.ConnectionParamsBuilder builder = MqttLib.ConnectionParams.builder()
                            .clientId(clientId)
                            .host(host)
                            .port(port)
                            .keepalive(keepalive)
                            .cleanSession(request.getCleanSession())
                            .connectTimeout(connectTimeout);

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
                logger.atWarn().log("invalid reason, must be in range [{}, {0}]", REASON_MIN, REASON_MAX);
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid reason, must be in range [0, 255]")
                                            .asRuntimeException());
                return;
            }

            MqttConnection connection = mqttLib.unregisterConnection(connectionId);
            if (connection == null) {
                logger.atWarn().log("connection with id {0} doesn't found");
                responseObserver.onError(Status.NOT_FOUND
                                            .withDescription("connection doesn't found")
                                            .asRuntimeException());
                return;
            }

            try {
                // TODO: pass also DISCONNECT properties
                connection.disconnect(reason);
            } catch (MqttException ex) {
                logger.atError().withThrowable(ex).log("exception during disconnect");
                responseObserver.onError(ex);
                return;
            }

            Empty reply = Empty.newBuilder().build();
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
