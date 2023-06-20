#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""Client of gRPC MqttAgentDiscovery service."""
# pylint: disable=no-member,no-name-in-module
import logging

import grpc
from exceptions.grpc_exception import GRPCException
from grpc_client_server.grpc_generated.mqtt_client_control_pb2 import Mqtt5Disconnect, MqttConnectionId
from grpc_client_server.grpc_generated import mqtt_client_control_pb2_grpc, mqtt_client_control_pb2


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
            self.__stub = mqtt_client_control_pb2_grpc.MqttAgentDiscoveryStub(self.__channel)
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
        request = mqtt_client_control_pb2.DiscoveryRequest(agentId=self.__agent_id, address=address, port=port)

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
        request = mqtt_client_control_pb2.RegisterRequest(agentId=self.__agent_id)
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
        request = mqtt_client_control_pb2.UnregisterRequest(agentId=self.__agent_id, reason=reason)
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

    def on_mqtt_disconnect(self, connection_id: int, disconnect_info: Mqtt5Disconnect, error_string: str):
        """
        Called when MQTT connection has been
        disconnected by client or server side.
        Parameters
        ----------
        connection_id - ID of disconnected connection
        disconnect_info - disconnect information
        error_string - specific error information
        """
        mqtt_connection_id = MqttConnectionId(connectionId=connection_id)
        request = mqtt_client_control_pb2.OnMqttDisconnectRequest(
            agentId=self.__agent_id, connectionId=mqtt_connection_id, disconnect=disconnect_info, error=error_string
        )

        self.__logger.info(
            "Sending OnMqttDisconnect request agent_id '%s' connection_id %i error '%s'",
            self.__agent_id,
            connection_id,
            error_string,
        )

        try:
            self.__stub.OnMqttDisconnect(request)
        except grpc.RpcError as error:
            self.__logger.exception(GRPC_REQUEST_FAILED)
            raise GRPCException(error) from error

    def close(self):
        """Closes the gRPC client."""
        self.__channel.close()
