#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""MQTT Library implementation."""

import logging
from mqtt_lib.mqtt_connection import MqttConnection, ConnectionParams
from mqtt_lib.temp_files_manager import TempFilesManager
from grpc_client_server.grpc_discovery_client import GRPCDiscoveryClient


class MQTTLib:
    """MQTT Library"""

    logger = logging.getLogger("MQTTLib")

    def __init__(self, temp_files_manager: TempFilesManager):
        """
        Construct MQTTLib
        Parameters
        ----------
        temp_files_manager - Temp files manager
        """
        self.__logger = MQTTLib.logger
        self.__logger.info("Initialize Paho MQTT library")
        self.__connection_id_next = 0
        self.__connections = {}
        self.__temp_files_manager = temp_files_manager

    def create_connection(
        self, connection_params: ConnectionParams, grpc_client: GRPCDiscoveryClient
    ) -> MqttConnection:
        """
        Create MQTTConnection object
        Parameters
        ----------
        connection_params - the connection parameters
        grpc_client - the consumer of received messages and disconnect events
        Returns MQTTConnection object
        """
        return MqttConnection(connection_params, grpc_client, self.__temp_files_manager)

    def register_connection(self, mqtt_connection: MqttConnection) -> int:
        """
        Register the MQTT connection.
        Parameters
        ----------
        mqtt_connection - connection to register
        Returns id of connection
        """
        while True:
            connection_id = self.__connection_id_next
            self.__connection_id_next += 1

            if connection_id not in self.__connections:
                self.__connections[connection_id] = mqtt_connection
                mqtt_connection.set_connection_id(connection_id)
                return connection_id

    def get_connection(self, connection_id: int) -> MqttConnection:
        """
        Get the MQTT connection.
        Parameters
        ----------
        connection_id - id of connection
        Returns MqttConnection on success or None when connection does not found
        """
        return self.__connections.get(connection_id)

    def unregister_connection(self, connection_id: int) -> MqttConnection:
        """
        Unregister the MQTT connection.
        Parameters
        ----------
        connection_id - id of connection
        Returns MqttConnection on success or None when connection does not found
        """
        return self.__connections.pop(connection_id, None)
