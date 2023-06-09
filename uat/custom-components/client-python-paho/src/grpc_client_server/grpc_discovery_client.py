#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""Client of gRPC MqttAgentDiscovery service."""
# pylint: disable=no-member
import logging

import grpc
from grpc_client_server.grpc_generated import mqtt_client_control_pb2_grpc
from grpc_client_server.grpc_generated import mqtt_client_control_pb2
from exceptions.grpc_exception import GRPCException


GRPC_REQUEST_FAILED = "gRPC request failed"


class GRPCDiscoveryClient:
    """Client of gRPC MqttAgentDiscovery service."""

    logger = logging.getLogger("GRPCDiscoveryClient")

    def __init__(self, agent_id: str, address: str):
        """
        Construct GRPCDiscoveryClient
        Parameters
        ----------
        agentId - id of agent to identify control channel by gRPC server
        address - address of gRPC service, including port
        """
        self.__logger = GRPCDiscoveryClient.logger
        self.__agent_id = agent_id
        self.__channel = grpc.insecure_channel(address)

        try:
            self.__stub = mqtt_client_control_pb2_grpc.MqttAgentDiscoveryStub(
                self.__channel
            )
        except Exception as error:
            self.close()
            raise error

    def discovery_agent(self, address: str, port: int):
        """
        Discover the agent.
        Parameters
        ----------
        address - host of local gRPC service
        port of local gRPC service
        """
        request = mqtt_client_control_pb2.DiscoveryRequest(
            agentId=self.__agent_id, address=address, port=port
        )

        try:
            self.__stub.DiscoveryAgent(request)
        except grpc.RpcError as error:
            self.__logger.exception(GRPC_REQUEST_FAILED)
            raise GRPCException(error) from error

    def register_agent(self) -> str:
        """
        Register the agent.
        Parameters
        ----------
        Returns IP address of client as visible by server
        """
        request = mqtt_client_control_pb2.RegisterRequest(
            agentId=self.__agent_id
        )
        reply = None

        try:
            reply = self.__stub.RegisterAgent(request)
        except grpc.RpcError as error:
            self.__logger.exception(GRPC_REQUEST_FAILED)
            raise GRPCException(error) from error

        return reply.address

    def unregister_agent(self, reason: str):
        """
        Unregister the agent.
        Parameters
        ----------
        reason - reason of unregistering
        """
        request = mqtt_client_control_pb2.UnregisterRequest(
            agentId=self.__agent_id, reason=reason
        )
        try:
            self.__stub.UnregisterAgent(request)
        except grpc.RpcError as error:
            self.__logger.exception(GRPC_REQUEST_FAILED)
            raise GRPCException(error) from error

    def on_receive_mqtt_message(self):
        """
        Called when MQTT message is receive my MQTT client
        and deliver information from it to gRPC server.
        """
        # TODO

    def on_mqtt_disconnect(self):
        """
        Called when MQTT connection has been
        disconnected by client or server side.
        """
        # TODO

    def close(self):
        """Closes the gRPC client."""
        self.__channel.close()
