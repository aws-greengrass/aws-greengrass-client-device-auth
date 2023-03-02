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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of gRPC server handles requests of OTF.
 */
class GRPCControlServer {
    private static final int PORT_MIN = 1;
    private static final int PORT_MAX = 65_535;

    private static final int KEEPALIVE_OFF = 0;
    private static final int KEEPALIVE_MIN = 5;
    private static final int KEEPALIVE_MAX = 65_535;

    private static final int REASON_MIN = 0;
    private static final int REASON_MAX = 255;

    private static final int CONNECT_TIMEOUT_MIN = 1;


    private static final Logger logger = Logger.getLogger(GRPCControlServer.class.getName());

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
            logger.log(Level.INFO, "shutdownAgent: {0}", new Object[]{shutdownReason});

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

            logger.log(Level.INFO, "createMqttConnection: clientId {0} broker {1}:{2}",
                            new Object[]{clientId, host, String.valueOf(port)});

            if (clientId == null || clientId.isEmpty()) {
                logger.log(Level.WARNING, "createMqttConnection: clientId can't be empty");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("clientId can't be empty")
                                            .asRuntimeException());
                return;
            }

            if (host == null || host.isEmpty()) {
                logger.log(Level.WARNING, "createMqttConnection: host can't be empty");
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("host can't be empty")
                                            .asRuntimeException());
                return;
            }

            if (port < PORT_MIN || port > PORT_MAX) {
                logger.log(Level.WARNING, "createMqttConnection: invalid port, must be in range [{0}, {1}]",
                                new Object[]{PORT_MIN,  String.valueOf(PORT_MAX)});
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid port, must be in range [1, 65535]")
                                            .asRuntimeException());
                return;
            }

            MqttProtoVersion version = request.getProtocolVersion();
            if (version != MqttProtoVersion.MQTT_PROTOCOL_V50) {
                logger.log(Level.WARNING, "createMqttConnection: MQTT_PROTOCOL_V50 is only supported but {0} requested",
                                new Object[]{version});
                responseObserver.onError(Status.INVALID_ARGUMENT
                                .withDescription("invalid protocolVersion, only MQTT_PROTOCOL_V50 is supported")
                                .asRuntimeException());
                return;
            }

            int keepalive = request.getKeepalive();
            if (keepalive != KEEPALIVE_OFF && (keepalive < KEEPALIVE_MIN || keepalive > KEEPALIVE_MAX)) {
                logger.log(Level.WARNING, "createMqttConnection: invalid keepalive, must be in range [{0}, {1}]",
                                new Object[]{KEEPALIVE_MIN,  String.valueOf(KEEPALIVE_MAX)});

                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid keepalive, must be in range [1, 65535]")
                                            .asRuntimeException());
                return;
            }


            int connectTimeout = request.getConnectTimeout();
            if (connectTimeout < CONNECT_TIMEOUT_MIN) {
                logger.log(Level.WARNING, "createMqttConnection: invalid connectTimeout, must be >= {0}",
                                new Object[]{String.valueOf(CONNECT_TIMEOUT_MIN)});

                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid connectTimeout, must be >= 1")
                                            .asRuntimeException());
                return;
            }
            MqttLib.ConnectionParams.ConnectionParamsBuilder builder = MqttLib.ConnectionParams.builder();

            // check TLS optional settings
            if (request.hasTls()) {
                TLSSettings tls = request.getTls();
                String ca = tls.getCa();

                if (ca == null || ca.isEmpty()) {
                    logger.log(Level.WARNING,  "createMqttConnection: ca is empty");
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("CA is empty")
                                                .asRuntimeException());
                    return;
                }

                String cert = tls.getCert();
                if (cert == null || cert.isEmpty()) {
                    logger.log(Level.WARNING,  "createMqttConnection: cert is empty");
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("cert is empty")
                                                .asRuntimeException());
                    return;
                }

                String key = tls.getKey();
                if (key == null || key.isEmpty()) {
                    logger.log(Level.WARNING, "createMqttConnection: key is empty");
                    responseObserver.onError(Status.INVALID_ARGUMENT
                                                .withDescription("key is empty")
                                                .asRuntimeException());
                    return;
                }

                builder.ca(ca).cert(cert).key(key);
            }

            MqttConnection connection;
            try {
                connection = mqttLib.createConnection(builder.clientId(clientId)
                            .host(host)
                            .port(port)
                            .keepalive(keepalive)
                            .cleanSession(request.getCleanSession())
                            .connectTimeout(connectTimeout)
                            .build());
            } catch (MqttException ex) {
                logger.log(Level.WARNING, "createMqttConnection: exception during connect", ex);
                responseObserver.onError(ex);
                return;
            }
            int connectionId = mqttLib.registerConnection(connection);

            MqttConnectionId reply = MqttConnectionId.newBuilder().setConnectionId(connectionId).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void closeMqttConnection(MqttCloseRequest request, StreamObserver<Empty> responseObserver) {

            int connectionId = request.getConnectionId().getConnectionId();
            int reason = request.getReason();
            logger.log(Level.INFO, "closeMqttConnection: connectionId {0} reason {1}",
                        new Object[]{connectionId, reason});

            if (reason < REASON_MIN || reason > REASON_MAX) {
                logger.log(Level.WARNING, "closeMqttConnection: invalid reason, must be in range [{0}, {1}]",
                                new Object[]{REASON_MIN, REASON_MAX});
                responseObserver.onError(Status.INVALID_ARGUMENT
                                            .withDescription("invalid reason, must be in range [0, 255]")
                                            .asRuntimeException());
                return;
            }

            MqttConnection connection = mqttLib.unregisterConnection(connectionId);
            if (connection == null) {
                logger.log(Level.WARNING, "closeMqttConnection: connection with id {0} doesn't found",
                                new Object[]{connectionId});
                responseObserver.onError(Status.NOT_FOUND
                                            .withDescription("connection doesn't found")
                                            .asRuntimeException());
                return;
            }

            try {
                // TODO: pass also DISCONNECT properties
                connection.disconnect(reason);
            } catch (MqttException ex) {
                logger.log(Level.WARNING, "closeMqttConnection: exception during disconnect", ex);
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
        logger.log(Level.INFO, "GRPCControlServer created and listed on {0}:{1}",
                    new Object[]{host, String.valueOf(boundPort)});
    }

    public int getPort() {
        return boundPort;
    }

    public String getShutdownReason() {
        return shutdownReason;
    }


    public void waiting(MqttLib mqttLib) throws InterruptedException {
        this.mqttLib = mqttLib;
        logger.log(Level.INFO, "Server awaitTermination");
        server.awaitTermination();
        logger.log(Level.INFO, "Server awaitTermination done");
    }

    public void close() {
        server.shutdown(); // or       shutdownNow()
    }
}
