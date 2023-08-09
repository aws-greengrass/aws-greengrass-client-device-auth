#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""GRPC Library implementation."""
import logging
from typing import List
from grpc_client_server.grpc_link import GRPCLink


class GRPCLib:  # pylint: disable=too-few-public-methods
    """GRPC Library"""

    logger = logging.getLogger("GRPCLib")

    def __init__(self):
        """Construct GRPCLib"""
        self.__logger = GRPCLib.logger
        self.__logger.info("Initialize gRPC library")

    async def make_link(self, agent_id: str, hosts: List[str], port: int) -> GRPCLink:
        """
        Creates and returns GRPCLink object.
        Args:
            agent_id: id of agent to identify control channel by server
            hosts: host names/IPs to connect to testing framework
            port: TCP port to connect to
        Returns:
            GRPCLink object
        """
        grpc_link = GRPCLink(agent_id, hosts, port)
        await grpc_link.start_server()
        return grpc_link
