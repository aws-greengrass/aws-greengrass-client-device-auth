#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""Paho Python client entry point."""
import logging
import asyncio
import sys
import argparse
from typing import List
from logger_setup import logger_setup

from grpc_client_server.grpc_lib import GRPCLib
from mqtt_lib.mqtt_lib import MQTTLib
from mqtt_lib.temp_files_manager import TempFilesManager
from exceptions.client_exception import ClientException


DEFAULT_GRPC_SERVER_IP = ["127.0.0.1"]
DEFAULT_GRPC_SERVER_PORT = 47_619

PORT_MIN = 1
PORT_MAX = 65_535


class Arguments:  # pylint: disable=too-few-public-methods
    """Class container for parsed input arguments"""

    def __init__(self, agent_id: str, hosts: List[str], port: int):
        """
        Construct Arguments
        Parameters
        ----------
        agent_id - id of agent to identify control channel by server
        hosts - host names/IPs to connect to testing framework
        port - TCP port to connect to
        """
        self.agent_id = agent_id
        self.hosts = hosts
        self.port = port


class Main:
    """Main"""

    def __init__(self):
        """Construct Main"""
        logger_setup.setup_log()
        self.__logger = logging.getLogger("Main")

    def parse_args(self) -> Arguments:
        """
        Setup and run.
        Parameters
        ----------
        Returns parsed arguments
        """
        parser = argparse.ArgumentParser()
        parser.add_argument(
            "agent_id",
            help="<Required> identification string" " for that agent for the control",
            type=str,
        )
        parser.add_argument(
            "port",
            nargs="?",
            help="TCP port of gRPC service of the control." f" Default value is {DEFAULT_GRPC_SERVER_PORT}",
            default=DEFAULT_GRPC_SERVER_PORT,
            type=int,
        )
        parser.add_argument(
            "ip",
            nargs="*",
            help="IP address of gRPC service of the control." f" Default value is {DEFAULT_GRPC_SERVER_IP}",
            default=DEFAULT_GRPC_SERVER_IP,
            type=str,
        )
        try:
            args = parser.parse_args()

        except SystemExit as error:
            raise TypeError("Invalid arguments") from error

        if (args.port < PORT_MIN) or (args.port > PORT_MAX):
            raise TypeError("Invalid port value " + args.port + " , expected [1..65535]")

        return Arguments(args.agent_id, args.ip, args.port)

    async def do_all(self, temp_files_manager: TempFilesManager):
        """
        Run program.
        Parameters
        ----------
        temp_files_manager - Temp files manager
        """
        arguments = self.parse_args()
        grpc_lib = GRPCLib()
        link = await grpc_lib.make_link(
            agent_id=arguments.agent_id,
            port=arguments.port,
            hosts=arguments.hosts,
        )

        mqtt_lib = MQTTLib(temp_files_manager)
        reason = await link.handle_requests(mqtt_lib)
        link.shutdown(reason)

    async def main(self) -> int:
        """
        Setup and run.
        Returns response code, which shows the shutdown reason
        """

        exit_code = 0
        temp_files_manager = TempFilesManager()

        try:
            await self.do_all(temp_files_manager)
            self.__logger.info("Execution done successfully")
        except TypeError:
            self.__logger.exception("Invalid arguments")
            self.print_usage()
            exit_code = 1
        except ClientException:
            self.__logger.exception("ClientException")
            exit_code = 2
        except Exception:  # pylint: disable=broad-exception-caught
            self.__logger.exception("Exception")
            exit_code = 3
        finally:
            temp_files_manager.destroy_all_files()

        return exit_code

    def print_usage(self):
        """Print programm usage"""
        self.__logger.warning(
            "Usage: agent_id [[port] ip ...]\n"
            "         agent_id\tidentification string for"
            " that agent for the control\n"
            "         port\tTCP port of gRPC service of the control\n"
            "         ip\tIP address of gRPC service of the control\n"
        )


if __name__ == "__main__":
    main_object = Main()
    if sys.version_info >= (3, 8) and sys.platform.lower().startswith("win"):
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    rc = asyncio.run(main_object.main())
    sys.exit(rc)
