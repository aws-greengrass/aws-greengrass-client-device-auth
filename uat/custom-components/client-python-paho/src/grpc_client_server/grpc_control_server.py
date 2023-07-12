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
    MqttConnectionId,
)
from mqtt_lib.mqtt_lib import MQTTLib
from mqtt_lib.mqtt_connection import ConnectionParams, MqttPubMessage, Subscribtion
from exceptions.mqtt_exception import MQTTException

PORT_MIN = 1
PORT_MAX = 65_535

KEEPALIVE_OFF = 0
KEEPALIVE_MIN = 5
KEEPALIVE_MAX = 65_535

TIMEOUT_MIN = 1

REASON_MIN = 0
REASON_MAX = 255

QOS_MIN = 0
QOS_MAX = 2

RETAIN_HANDLING_MIN = 0
RETAIN_HANDLING_MAX = 2


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

        self.__client = client

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

        request_response_information = None
        if request.HasField("requestResponseInformation"):
            request_response_information = request.requestResponseInformation

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
            mqtt_properties=request.properties,
            request_response_information=request_response_information,
        )

        try:
            connection = self.__mqtt_lib.create_connection(
                connection_params=connection_params, grpc_client=self.__client
            )
            mqtt_connection_id = MqttConnectionId(connectionId=self.__mqtt_lib.register_connection(connection))
            connect_result = await connection.start(request.timeout)

            connect_reply = MqttConnectReply(
                connected=connect_result.connected,
                connectionId=mqtt_connection_id,
                connAck=connect_result.conn_ack_info,
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
        if not request.HasField("msg"):
            self.__logger.warning("PublishMqtt: message is missing")
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "message is missing")

        message = request.msg

        if (message.qos < QOS_MIN) or (message.qos > QOS_MAX):
            self.__logger.warning(
                "PublishMqtt: invalid QoS %i, must be in range [%i,%i]", message.qos, QOS_MIN, QOS_MAX
            )
            await context.abort(
                grpc.StatusCode.INVALID_ARGUMENT, f"invalid QoS, must be in range [{QOS_MIN},{QOS_MAX}]"
            )

        if not message.topic:
            self.__logger.warning("PublishMqtt: topic is empty")
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "topic is empty")

        timeout = request.timeout
        if request.timeout < TIMEOUT_MIN:
            self.__logger.warning("PublishMqtt: invalid timeout, must be at least %i second", TIMEOUT_MIN)
            await context.abort(
                grpc.StatusCode.INVALID_ARGUMENT, f"invalid publish timeout, must be at least {TIMEOUT_MIN}"
            )

        if not request.HasField("connectionId"):
            self.__logger.warning("PublishMqtt: missing connection id")
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "missing connection id")

        content_type = None
        if message.HasField("contentType"):
            content_type = message.contentType

        payload_format_indicator = None
        if message.HasField("payloadFormatIndicator"):
            payload_format_indicator = message.payloadFormatIndicator

        message_expiry_interval = None
        if message.HasField("messageExpiryInterval"):
            message_expiry_interval = message.messageExpiryInterval

        response_topic = None
        if message.HasField("responseTopic"):
            response_topic = message.responseTopic

        correlation_data = None
        if message.HasField("correlationData"):
            correlation_data = message.correlationData

        connection_id = request.connectionId.connectionId
        self.__logger.info(
            "PublishMqtt connection_id %i topic %s retain %i", connection_id, message.topic, int(message.retain)
        )

        connection = self.__mqtt_lib.get_connection(connection_id)
        if connection is None:
            self.__logger.warning("PublishMqtt: connection with id %i is not found", connection_id)
            await context.abort(grpc.StatusCode.NOT_FOUND, "connection for that id is not found")

        try:
            publish_reply = await connection.publish(
                timeout=timeout,
                message=MqttPubMessage(
                    qos=message.qos,
                    retain=message.retain,
                    topic=message.topic,
                    payload=message.payload,
                    response_topic=response_topic,
                    correlation_data=correlation_data,
                    mqtt_properties=message.properties,
                    content_type=content_type,
                    payload_format_indicator=payload_format_indicator,
                    message_expiry_interval=message_expiry_interval,
                ),
            )
            return publish_reply
        except MQTTException as error:
            self.__logger.warning("PublishMqtt: exception during publishing")
            await context.abort(grpc.StatusCode.INTERNAL, str(error))

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
        timeout = request.timeout
        if timeout < TIMEOUT_MIN:
            self.__logger.warning("CloseMqttConnection: invalid timeout, must be at least %i second", TIMEOUT_MIN)
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, f"invalid timeout, must be at least {TIMEOUT_MIN}")

        reason = request.reason
        if (reason < REASON_MIN) or (reason > REASON_MAX):
            self.__logger.warning("CloseMqttConnection: invalid disconnect reason %i", reason)
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "invalid disconnect reason")

        if not request.HasField("connectionId"):
            self.__logger.warning("CloseMqttConnection: missing connection id")
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "missing connection id")

        connection_id = request.connectionId.connectionId
        self.__logger.info("CloseMqttConnection connection_id %i reason %i", connection_id, reason)

        connection = self.__mqtt_lib.unregister_connection(connection_id)
        if connection is None:
            self.__logger.warning("CloseMqttConnection: connection with id %i is not found", connection_id)
            await context.abort(grpc.StatusCode.NOT_FOUND, "connection for that id is not found")

        try:
            await connection.disconnect(timeout=timeout, reason=reason, mqtt_properties=request.properties)
            return Empty()
        except MQTTException as error:
            self.__logger.warning("CloseMqttConnection: exception during disconnecting")
            await context.abort(grpc.StatusCode.INTERNAL, str(error))

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
        timeout = request.timeout
        if timeout < TIMEOUT_MIN:
            self.__logger.warning("SubscribeMqtt: invalid timeout, must be at least %i second", TIMEOUT_MIN)
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, f"invalid timeout, must be at least {TIMEOUT_MIN}")

        index = 0
        subscribtions = []
        for mqtt_sub in request.subscriptions:
            if not mqtt_sub.filter:
                self.__logger.warning("SubscribeMqtt: empty filter at subscription index %i", index)
                await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "empty filter")

            if (mqtt_sub.qos < QOS_MIN) or (mqtt_sub.qos > QOS_MAX):
                self.__logger.warning(
                    "SubscribeMqtt: invalid QoS %i at subscription index %i, must be in range [%i,%i]",
                    mqtt_sub.qos,
                    index,
                    QOS_MIN,
                    QOS_MAX,
                )
                await context.abort(
                    grpc.StatusCode.INVALID_ARGUMENT, f"invalid QoS, must be in range [{QOS_MIN},{QOS_MAX}]"
                )

            if (mqtt_sub.retainHandling < RETAIN_HANDLING_MIN) or (mqtt_sub.retainHandling > RETAIN_HANDLING_MAX):
                self.__logger.warning(
                    "SubscribeMqtt: invalid retainHandling %i at subscription index %i, must be in range [%i,%i]",
                    mqtt_sub.retainHandling,
                    index,
                    RETAIN_HANDLING_MIN,
                    RETAIN_HANDLING_MAX,
                )
                await context.abort(
                    grpc.StatusCode.INVALID_ARGUMENT,
                    f"invalid QoS, must be in range [{RETAIN_HANDLING_MIN},{RETAIN_HANDLING_MAX}]",
                )

            new_subscription = Subscribtion(
                filter=mqtt_sub.filter,
                qos=mqtt_sub.qos,
                no_local=mqtt_sub.noLocal,
                retain_as_published=mqtt_sub.retainAsPublished,
                retain_handling=mqtt_sub.retainHandling,
            )

            subscribtions.append(new_subscription)

            self.__logger.debug(
                "Subscription: filter %s QoS %i noLocal %i retainAsPublished %i retainHandling %i",
                new_subscription.filter,
                new_subscription.qos,
                new_subscription.no_local,
                new_subscription.retain_as_published,
                new_subscription.retain_handling,
            )

        connection_id = request.connectionId.connectionId
        self.__logger.info("SubscribeMqtt connection_id %i", connection_id)

        connection = self.__mqtt_lib.get_connection(connection_id)
        if connection is None:
            self.__logger.warning("SubscribeMqtt: connection with id %i is not found", connection_id)
            await context.abort(grpc.StatusCode.NOT_FOUND, "connection for that id is not found")

        try:
            sub_reply = await connection.subscribe(
                timeout=timeout, subscriptions=subscribtions, mqtt_properties=request.properties
            )
            return sub_reply
        except MQTTException as error:
            self.__logger.warning("SubscribeMqtt: exception during subscribing")
            await context.abort(grpc.StatusCode.INTERNAL, str(error))

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

        timeout = request.timeout
        if timeout < TIMEOUT_MIN:
            self.__logger.warning("UnsubscribeMqtt: invalid timeout, must be at least %i second", TIMEOUT_MIN)
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, f"invalid timeout, must be at least {TIMEOUT_MIN}")

        index = 0
        filters = []
        for topic_filter in request.filters:
            if not topic_filter:
                self.__logger.warning("UnubscribeMqtt: empty filter at unsubscription index %i", index)
                await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "empty filter")

            filters.append(topic_filter)

            self.__logger.debug("Unsubscription: filter %s", topic_filter)

        connection_id = request.connectionId.connectionId
        self.__logger.info("UnsubscribeMqtt connection_id %i", connection_id)

        connection = self.__mqtt_lib.get_connection(connection_id)
        if connection is None:
            self.__logger.warning("UnsubscribeMqtt: connection with id %i is not found", connection_id)
            await context.abort(grpc.StatusCode.NOT_FOUND, "connection for that id is not found")

        try:
            unsub_reply = await connection.unsubscribe(
                timeout=timeout, filters=filters, mqtt_properties=request.properties
            )
            return unsub_reply
        except MQTTException as error:
            self.__logger.warning("UnsubscribeMqtt: exception during subscribing")
            await context.abort(grpc.StatusCode.INTERNAL, str(error))

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
        if not request.clientId:
            self.__logger.warning("CreateMqttConnection: clientId can't be empty")
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, "clientId can't be empty")

        if not request.host:
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
            self.__logger.warning("CreateMqttConnection: invalid timeout, must be at least %i second", TIMEOUT_MIN)
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, f"invalid timeout, must be at least {TIMEOUT_MIN}")
