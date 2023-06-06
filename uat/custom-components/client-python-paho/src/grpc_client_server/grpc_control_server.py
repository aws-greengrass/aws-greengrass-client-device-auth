# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0.

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
)


class GRPCControlServer(
    mqtt_client_control_pb2_grpc.MqttClientControlServicer
):
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

        # TODO delete suppress warning
        self.__client = client  # pylint: disable=unused-private-member

        super().__init__()

        self.__server = grpc.aio.server()
        mqtt_client_control_pb2_grpc.add_MqttClientControlServicer_to_server(
            self, self.__server
        )
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

    async def wait(self):
        """Wait until incoming shutdown request"""
        self.__logger.info("Server awaiting termination")
        await self.__stop_server_future
        await self.__server.stop(None)
        await self.__server.wait_for_termination()
        self.__logger.info("Server termination done")

    def unblock_wait(self):
        """Unblock wait method"""
        try:
            self.__stop_server_future.set_result(True)
        except asyncio.base_futures.InvalidStateError:
            pass

    def close(self):
        """Closes the gRPC server."""
        self.unblock_wait()

    async def ShutdownAgent(
        self, request: ShutdownRequest, context: grpc.aio.ServicerContext
    ) -> Empty:
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
        # TODO
        self.__logger.info("CreateMqttConnection Placeholder TODO")
        return MqttConnectReply()

    async def PublishMqtt(
        self, request: MqttPublishRequest, context: grpc.aio.ServicerContext
    ) -> MqttPublishReply:
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

    async def CloseMqttConnection(
        self, request: MqttCloseRequest, context: grpc.aio.ServicerContext
    ) -> Empty:
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
