#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""GRPC Link implementation."""
import logging
from typing import List

from grpc_client_server.grpc_control_server import GRPCControlServer
from grpc_client_server.grpc_discovery_client import GRPCDiscoveryClient
from mqtt_lib.mqtt_lib import MQTTLib
from exceptions.grpc_exception import GRPCException

AUTOSELECT_PORT = 0


class ClientConnectResult:  # pylint: disable=too-few-public-methods
    """Class container for connect results"""

    def __init__(self, client: GRPCDiscoveryClient, local_ip: str):
        """
        Construct ClientConnectResult
        Parameters
        ----------
        client - Discovery client object
        local_ip - local client IP address
        """
        self.client = client
        self.local_ip = local_ip


class GRPCLink:
    """GRPC Link"""

    logger = logging.getLogger("GRPCLink")

    def __init__(self, agent_id: str, hosts: List[str], port: int):
        """
        Construct GRPCLink.
        Parameters
        ----------
        agent_id - id of agent to identify control channel by server
        hosts - host names/IPs to connect to testing framework
        port - TCP port to connect to
        """
        self.__logger = GRPCLink.logger

        result = self.__make_clients_connection(agent_id, hosts, port)
        client = result.client

        local_address = GRPCLink.build_address(result.local_ip, AUTOSELECT_PORT)

        self.__server = GRPCControlServer(client, local_address)
        self.__local_ip = result.local_ip
        self.__client = client

    async def start_server(self):
        """
        Connect to the GRPC server.
        """
        await self.__server.start_grpc_server()
        service_port = self.__server.get_port()
        self.__client.discovery_agent(self.__local_ip, service_port)

    async def handle_requests(self, mqtt_lib: MQTTLib) -> str:
        """
        Handle gRPC requests.
        Parameters
        ----------
        Returns shutdown reason
        """
        self.__logger.info("Handle gRPC requests")
        await self.__server.wait(mqtt_lib)
        return "Agent shutdown by OTF request '" + self.__server.get_shutdown_reason() + "'"

    def shutdown(self, reason: str):
        """
        Unregister MQTT client control in testing framework.
        Parameters
        ----------
        reason - reason of shutdown
        """
        self.__logger.info("Shutdown gPRC link")
        self.__client.unregister_agent(reason)
        self.__server.close()
        self.__client.close()

    @staticmethod
    def build_address(host: str, port: int) -> str:
        """
        Build full address from name/IP and port.
        Parameters
        ----------
        host - host name/IP to connect to testing framework
        port - TCP port to connect to
        """
        return host + ":" + str(port)

    def __make_clients_connection(self, agent_id: str, hosts: List[str], port: int):
        """
        Search for the available host.
        Parameters
        ----------
        agent_id - id of agent to identify control channel by server
        hosts - host names/IPs to connect to testing framework
        port - TCP port to connect to
        """
        last_exception = None
        try:
            for host in hosts:
                self.__logger.info(
                    "Making gPRC client connection with %s:%i as %s...",
                    host,
                    port,
                    agent_id,
                )

                otf_address = GRPCLink.build_address(host, port)
                client = GRPCDiscoveryClient(agent_id, otf_address)
                local_ip = client.register_agent()
                self.__logger.info(
                    "Client connection with Control is established, local address is %s",
                    local_ip,
                )
                return ClientConnectResult(client, local_ip)
        except GRPCException as error:
            last_exception = error

        raise last_exception
