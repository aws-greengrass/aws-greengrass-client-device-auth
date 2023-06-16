#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""GRPC Control server implementation."""
# pylint: disable=no-name-in-module,invalid-overridden-method
import asyncio
import logging
import grpc

from grpc_client_server.grpc_discovery_client import GRPCDiscoveryClient
from grpc_client_server.grpc_generated import mqtt_client_control_pb2_grpc
from grpc_client_server.grpc_generated.mqtt_client_control_pb2 import (
    Empty,
    ShutdownRequest,
    MqttConnectRequest,
    MqttConnectReply,
    MqttCloseRequest,
    MqttSubscribeRequest,
    MqttSubscribeReply,
    MqttUnsubscribeRequest,
    MqttPublishRequest,
    MqttPublishReply,
    MqttProtoVersion,
    Mqtt5ConnAck,
    MqttConnectionId,
)
from mqtt_lib.mqtt_lib import MQTTLib
from mqtt_lib.mqtt_connection import ConnAckInfo, ConnectionParams
from exceptions.mqtt_exception import MQTTException

PORT_MIN = 1
PORT_MAX = 65_535

KEEPALIVE_OFF = 0
KEEPALIVE_MIN = 5
KEEPALIVE_MAX = 65_535

TIMEOUT_MIN = 1


class GRPCControlServer(mqtt_client_control_pb2_grpc.MqttClientControlServicer):
    """Implementation of gRPC server handles requests of OTF."""

    logger = logging.getLogger("GRPCControlServer")

    def __init__(self, client: GRPCDiscoveryClient, address: str):
        """
        Construct GRPCControlServer
        Parameters
        ----------
        client - GRPCDiscoveryClient object
        address - local gRPC service address
        """
        self.__logger = GRPCControlServer.logger
        self.__shutdown_reason = ""
        self.__stop_server_future = asyncio.Future()
        self.__mqtt_lib: MQTTLib = None

        # TODO delete suppress warning
        self.__client = client  # pylint: disable=unused-private-member

        super().__init__()

        self.__server = grpc.aio.server()
        mqtt_client_control_pb2_grpc.add_MqttClientControlServicer_to_server(self, self.__server)
        self.__chosen_port = self.__server.add_insecure_port(address)

    def get_port(self) -> int:
        """Returns gRPC server port"""
        return self.__chosen_port

    def get_shutdown_reason(self) -> str:
        """Returns shutdown reason"""
        return self.__shutdown_reason

    async def start_grpc_server(self):
        """Start grpc server"""
        await self.__server.start()

    async def wait(self, mqtt_lib: MQTTLib):
        """
        Wait until incoming shutdown request
        Parameters
        ----------
        mqtt_lib - MQTT side of the client to handler incoming requests
        """
        self.__mqtt_lib = mqtt_lib
        self.__logger.info("Server awaiting termination")
        await self.__stop_server_future
        await self.__server.stop(None)
        await self.__server.wait_for_termination()
        self.__logger.info("Server termination done")

    def unblock_wait(self):
        """Unblock wait method"""
        try:
            self.__stop_server_future.set_result(True)
        except asyncio.InvalidStateError:
            pass

    def close(self):
        """Closes the gRPC server."""
        self.unblock_wait()

    async def ShutdownAgent(self, request: ShutdownRequest, context: grpc.aio.ServicerContext) -> Empty:
        """
        override
        Handler of ShutdownAgent gRPC call.
        Parameters
        ----------
        request - incoming request
        context - request context
        Returns Empty object
        """
        self.__shutdown_reason = request.reason
        self.__logger.info("shutdownAgent: reason %s", self.__shutdown_reason)
        self.unblock_wait()
        return Empty()

    async def CreateMqttConnection(
        self, request: MqttConnectRequest, context: grpc.aio.ServicerContext
    ) -> MqttConnectReply:
        """
        override
        Handler of CreateMqttConnection gRPC call.
        Parameters
        ----------
        request - incoming request
        context - request context
        Returns MqttConnectReply object
        """
        await self.__check_connect_request(request, context)

        mqtt_v5 = False

        if request.protocolVersion == MqttProtoVersion.MQTT_PROTOCOL_V_50:
            mqtt_v5 = True

        ca_cert = None
        cert = None
        key = None
        if request.HasField("tls"):
            tls_settings = request.tls
            ca_cert = "\n".join(tls_settings.caList)
            cert = tls_settings.cert
            key = tls_settings.key

            if not ca_cert:
                self.__logger.warning("CreateMqttConnection: ca is empty")
                await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "CA list is empty")

            if not cert:
                self.__logger.warning("CreateMqttConnection: cert is empty")
                await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "cert is empty")

            if not key:
                self.__logger.warning("CreateMqttConnection: key is empty")
                await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "key is empty")

        self.__logger.info(
            "createMqttConnection: clientId %s broker %s:%i", request.clientId, request.host, request.port
        )

        connection_params = ConnectionParams(
            client_id=request.clientId,
            host=request.host,
            port=request.port,
            keep_alive=request.keepalive,
            clean_session=request.cleanSession,
            mqtt50=mqtt_v5,
            ca_cert=ca_cert,
            cert=cert,
            key=key,
        )

        try:
            connection = self.__mqtt_lib.create_connection(
                connection_params=connection_params, grpc_client=self.__client
            )
            mqtt_connection_id = MqttConnectionId(connectionId=self.__mqtt_lib.register_connection(connection))
            connect_result = await connection.start(request.timeout)

            mqtt_conn_ack = None
            if connect_result.conn_ack_info is not None:
                mqtt_conn_ack = GRPCControlServer.convert_conn_ack(conn_ack_info=connect_result.conn_ack_info)

            connect_reply = MqttConnectReply(
                connected=connect_result.connected, connectionId=mqtt_connection_id, connAck=mqtt_conn_ack
            )

            if connect_result.error is not None:
                connect_reply.error = connect_result.error

            return connect_reply

        except MQTTException as error:
            self.__logger.exception("CreateMqttConnection: exception during connecting")
            await context.abort(grpc.StatusCode.INTERNAL, str(error))

        return MqttConnectReply()

    async def PublishMqtt(self, request: MqttPublishRequest, context: grpc.aio.ServicerContext) -> MqttPublishReply:
        """
        override
        Handler of PublishMqtt gRPC call.
        Parameters
        ----------
        request - incoming request
        context - request context
        Returns MqttPublishReply object
        """
        # TODO
        self.__logger.info("PublishMqtt Placeholder TODO")
        return MqttPublishReply()

    async def CloseMqttConnection(self, request: MqttCloseRequest, context: grpc.aio.ServicerContext) -> Empty:
        """
        override
        Handler of CloseMqttConnection gRPC call.
        Parameters
        ----------
        request - incoming request
        context - request context
        Returns Empty object
        """
        # TODO
        self.__logger.info("CloseMqttConnection Placeholder TODO")
        return Empty()

    async def SubscribeMqtt(
        self, request: MqttSubscribeRequest, context: grpc.aio.ServicerContext
    ) -> MqttSubscribeReply:
        """
        override
        Handler of SubscribeMqtt gRPC call.
        Parameters
        ----------
        request - incoming request
        context - request context
        Returns MqttSubscribeReply object
        """
        # TODO
        self.__logger.info("SubscribeMqtt Placeholder TODO")
        return MqttSubscribeReply()

    async def UnsubscribeMqtt(
        self,
        request: MqttUnsubscribeRequest,
        context: grpc.aio.ServicerContext,
    ) -> MqttSubscribeReply:
        """
        override
        Handler of UnsubscribeMqtt gRPC call.
        Parameters
        ----------
        request - incoming request
        context - request context
        Returns MqttSubscribeReply object
        """
        # TODO
        self.__logger.info("UnsubscribeRequest Placeholder TODO")
        return MqttSubscribeReply()

    async def __check_connect_request(self, request: MqttConnectRequest, context: grpc.aio.ServicerContext):
        """
        Check that mqtt connect request is correct.
        Parameters
        ----------
        request - incoming request
        context - request context
        Returns MqttConnectReply object
        """
        if (request.clientId is None) or not request.clientId:
            self.__logger.warning("CreateMqttConnection: clientId can't be empty")
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "clientId can't be empty")

        if (request.host is None) or not request.host:
            self.__logger.warning("CreateMqttConnection: host can't be empty")
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "host can't be empty")

        if (request.port < PORT_MIN) or (request.port > PORT_MAX):
            self.__logger.warning(
                "CreateMqttConnection: invalid port %i, must be in range [%i, %i]", request.port, PORT_MIN, PORT_MAX
            )
            await context.abort(
                grpc.StatusCode.INVALID_ARGUMENT, f"invalid port, must be in range [{PORT_MIN}, {PORT_MAX}]"
            )

        if request.protocolVersion not in (MqttProtoVersion.MQTT_PROTOCOL_V_311, MqttProtoVersion.MQTT_PROTOCOL_V_50):
            self.__logger.warning(
                "CreateMqttConnection: MQTT_PROTOCOL_V_311 or MQTT_PROTOCOL_V_50 are only supported but %i requested",
                request.protocolVersion,
            )
            await context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                "invalid protocolVersion, only MQTT_PROTOCOL_V_311 and MQTT_PROTOCOL_V_50 are supported",
            )

        if (request.keepalive != KEEPALIVE_OFF) and (
            (request.keepalive < KEEPALIVE_MIN) or (request.keepalive > KEEPALIVE_MAX)
        ):
            self.__logger.warning(
                "CreateMqttConnection: invalid keepalive, must be in range [%i, %i]", KEEPALIVE_MIN, KEEPALIVE_MAX
            )
            await context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                f"invalid keepalive, must be in range [{KEEPALIVE_MIN}, {KEEPALIVE_MAX}]",
            )

        if request.timeout < TIMEOUT_MIN:
            self.__logger.warning("CreateMqttConnection: invalid timeout, must be at least %i second\n", TIMEOUT_MIN)
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, f"invalid timeout, must be at least {TIMEOUT_MIN}")

    @staticmethod
    def convert_conn_ack(conn_ack_info: ConnAckInfo) -> Mqtt5ConnAck:
        """
        Convert ConnAck info to Mqtt5ConnAck
        Parameters
        ----------
        conn_ack_info - ConnAckInfo object
        Returns Mqtt5ConnAck object
        """
        conn_ack = Mqtt5ConnAck(
            sessionPresent=conn_ack_info.session_present,
            reasonCode=conn_ack_info.reason_code,
            sessionExpiryInterval=conn_ack_info.session_expiry_interval,
            maximumQoS=conn_ack_info.maximum_qos,
            retainAvailable=conn_ack_info.retain_available,
            maximumPacketSize=conn_ack_info.maximum_packet_size,
            assignedClientId=conn_ack_info.assigned_client_id,
            reasonString=conn_ack_info.reason_string,
            wildcardSubscriptionsAvailable=conn_ack_info.wildcard_subscriptions_available,
            subscriptionIdentifiersAvailable=conn_ack_info.subscription_identifiers_available,
            sharedSubscriptionsAvailable=conn_ack_info.shared_subscriptions_available,
            serverKeepAlive=conn_ack_info.server_keep_alive,
            responseInformation=conn_ack_info.response_information,
            serverReference=conn_ack_info.server_reference,
            topicAliasMaximum=conn_ack_info.topic_alias_maximum,
        )

        return conn_ack
