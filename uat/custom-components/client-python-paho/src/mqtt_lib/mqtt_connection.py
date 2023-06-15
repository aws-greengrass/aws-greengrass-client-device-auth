#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""MQTT5 Connection."""

import asyncio
import logging
import socket

from dataclasses import dataclass

import paho.mqtt.client as mqtt

from paho.mqtt import MQTTException as PahoException
from paho.mqtt.properties import Properties

from grpc_client_server.grpc_discovery_client import GRPCDiscoveryClient
from mqtt_lib.temp_files_manager import TempFilesManager
from exceptions.mqtt_exception import MQTTException


@dataclass
class MqttResult:
    """Class MqttResult"""

    reason_code: int
    flags: dict
    properties: Properties
    mid: int = 0


@dataclass
class ConnectionParams:  # pylint: disable=too-many-instance-attributes
    """MQTT connection parameters."""

    # MQTT client id.
    client_id: str
    # Host name of IP address of MQTT broker.
    host: str
    # Port of MQTT broker.
    port: int
    # Connection keep alive interval in seconds.
    keep_alive: int
    # Clean session (clean start) flag of CONNECT packet.
    clean_session: bool
    # The true MQTT v5.0 connection is requested.
    mqtt50: bool
    # Content of CA, can be None.
    ca_cert: str = None
    # Content of MQTT client's certificate, can be None.
    cert: str = None
    # Content of MQTT client's private key, can be None.
    key: str = None


@dataclass
class ConnAckInfo:  # pylint: disable=too-many-instance-attributes
    """Class container for connect ConnAck information"""

    session_present: bool = None
    reason_code: int = None
    session_expiry_interval: int = None
    receive_maximum: int = None
    maximum_qos: int = None
    retain_available: bool = None
    maximum_packet_size: int = None
    assigned_client_id: str = None
    reason_string: str = None
    wildcard_subscriptions_available: bool = None
    subscription_identifiers_available: bool = None
    shared_subscriptions_available: bool = None
    server_keep_alive: int = None
    response_information: str = None
    server_reference: str = None
    topic_alias_maximum: int = None

    # TODO add fields


@dataclass
class ConnectResult:  # pylint: disable=too-many-instance-attributes
    """Class container for connect results"""

    # Useful information from CONNACK packet. Can be missed
    connected: bool
    conn_ack_info: ConnAckInfo
    error: str


class AsyncioHelper:
    """Class Asyncio helper for the Paho MQTT"""

    def __init__(self, loop, client):
        """Construct AsyncioHelper"""
        self.misc = None
        self.loop = loop
        self.client = client
        self.client.on_socket_open = self.on_socket_open
        self.client.on_socket_close = self.on_socket_close
        self.client.on_socket_register_write = self.on_socket_register_write
        self.client.on_socket_unregister_write = self.on_socket_unregister_write

    def on_socket_open(self, client, userdata, sock):  # pylint: disable=unused-argument
        """Client socket open callback"""

        def callback():
            """Loop Reader callback"""
            client.loop_read()

        self.loop.add_reader(sock, callback)
        self.misc = self.loop.create_task(self.misc_loop())

    def on_socket_close(self, client, userdata, sock):  # pylint: disable=unused-argument
        """Client socket close callback"""

        self.loop.remove_reader(sock)
        self.misc.cancel()

    def on_socket_register_write(self, client, userdata, sock):  # pylint: disable=unused-argument
        """Client socket register write"""

        def callback():
            client.loop_write()

        self.loop.add_writer(sock, callback)

    def on_socket_unregister_write(self, client, userdata, sock):  # pylint: disable=unused-argument
        """Client socket unregister write"""

        self.loop.remove_writer(sock)

    async def misc_loop(self):
        """Client Misc Loop"""
        while self.client.loop_misc() == mqtt.MQTT_ERR_SUCCESS:
            try:
                await asyncio.sleep(1)
            except asyncio.CancelledError:
                break


class MqttConnection:  # pylint: disable=too-many-instance-attributes
    """MQTT Connection."""

    logger = logging.getLogger("MqttConnection")

    def __init__(
        self,
        connection_params: ConnectionParams,
        grpc_client: GRPCDiscoveryClient,
        temp_files_manager: TempFilesManager,
    ):
        """
        Construct MQTTConnection
        Parameters
        ----------
        connection_params - the connection parameters
        grpc_client - the consumer of received messages and disconnect events
        """
        super().__init__()
        self.__logger = MqttConnection.logger
        self.__temp_files_manager = temp_files_manager
        self.__connection_params = connection_params

        protocol = mqtt.MQTTv311

        if self.__connection_params.mqtt50:
            protocol = mqtt.MQTTv5

        self.__protocol = protocol
        self.__client = self.__create_client(self.__connection_params)
        # TODO delete 2 suppress warnings
        self.__grpc_client = grpc_client  # pylint: disable=unused-private-member
        self.__connection_id = 0  # pylint: disable=unused-private-member
        self.__on_connect_future = None
        self.__asyncio_helper = None  # pylint: disable=unused-private-member
        self.__loop = asyncio.get_running_loop()

    def set_connection_id(self, connection_id: int):
        """
        Connection id setter
        Parameters
        ----------
        connection_id - connection id as assigned by MQTT library
        """
        # TODO delete suppress warnings
        self.__connection_id = connection_id  # pylint: disable=unused-private-member

    async def start(self, timeout: int) -> ConnectResult:
        """
        Starts MQTT connection.
        Parameters
        ----------
        timeout - connect operation timeout in seconds
        Returns connection result
        """
        self.__client.on_connect = self.__on_connect
        self.__asyncio_helper = AsyncioHelper(self.__loop, self.__client)  # pylint: disable=unused-private-member

        clean_start = self.__connection_params.clean_session

        # Clean Start is used only for the MQTT 5
        if self.__protocol == mqtt.MQTTv311:
            clean_start = mqtt.MQTT_CLEAN_START_FIRST_ONLY

        try:
            self.__on_connect_future = self.__loop.create_future()
            self.__client.connect(
                host=self.__connection_params.host,
                port=self.__connection_params.port,
                keepalive=self.__connection_params.keep_alive,
                clean_start=clean_start,
            )

            result = await asyncio.wait_for(self.__on_connect_future, timeout)
            conn_ack_info = MqttConnection.convert_to_conn_ack(result)
            return ConnectResult(connected=True, conn_ack_info=conn_ack_info, error=None)
        except asyncio.TimeoutError:
            self.__logger.exception("Exception occurred during connect")
            return ConnectResult(connected=False, conn_ack_info=None, error="Connect timeout error")
        except (PahoException, socket.timeout) as error:
            self.__logger.exception("Exception occurred during connect")
            return ConnectResult(connected=False, conn_ack_info=None, error=str(error))
        except Exception as error:
            self.__logger.exception("Exception occurred during connect")
            raise MQTTException from error

    def __on_connect(
        self, client, userdata, flags, reason_code, properties=None
    ):  # pylint: disable=unused-argument,too-many-arguments
        """
        Paho MQTT callback
        """
        mqtt_reason_code = reason_code
        if hasattr(reason_code, "value"):
            mqtt_reason_code = reason_code.value
        mqtt_result = MqttResult(reason_code=mqtt_reason_code, flags=flags, properties=properties)
        try:
            if self.__on_connect_future is not None:
                self.__on_connect_future.set_result(mqtt_result)
        except asyncio.InvalidStateError:
            pass

    @staticmethod
    def convert_to_conn_ack(mqtt_result: MqttResult) -> ConnAckInfo:  # pylint: disable=too-many-branches
        """
        Convert MqttResult info to ConnAckInfo
        Parameters
        ----------
        mqtt_result - MqttResult object
        Returns ConnAckInfo object
        """
        conn_ack_info = ConnAckInfo()
        conn_ack_info.reason_code = mqtt_result.reason_code
        conn_ack_info.session_present = mqtt_result.flags["session present"]
        props = mqtt_result.properties

        if props is not None:
            if hasattr(props, "SessionExpiryInterval"):
                conn_ack_info.session_expiry_interval = props.SessionExpiryInterval

            if hasattr(props, "ReceiveMaximum"):
                conn_ack_info.receive_maximum = props.ReceiveMaximum

            if hasattr(props, "MaximumQoS"):
                conn_ack_info.maximum_qos = props.MaximumQoS

            if hasattr(props, "RetainAvailable"):
                conn_ack_info.retain_available = props.RetainAvailable

            if hasattr(props, "MaximumPacketSize"):
                conn_ack_info.maximum_packet_size = props.MaximumPacketSize

            if hasattr(props, "AssignedClientIdentifier"):
                conn_ack_info.assigned_client_id = props.AssignedClientIdentifier

            if hasattr(props, "ReasonString"):
                conn_ack_info.reason_string = props.ReasonString

            if hasattr(props, "WildcardSubscriptionAvailable"):
                conn_ack_info.wildcard_subscriptions_available = props.WildcardSubscriptionAvailable

            if hasattr(props, "SubscriptionIdentifierAvailable"):
                conn_ack_info.subscription_identifiers_available = props.SubscriptionIdentifierAvailable

            if hasattr(props, "SharedSubscriptionAvailable"):
                conn_ack_info.shared_subscriptions_available = props.SharedSubscriptionAvailable

            if hasattr(props, "ServerKeepAlive"):
                conn_ack_info.server_keep_alive = props.ServerKeepAlive

            if hasattr(props, "ResponseInformation"):
                conn_ack_info.response_information = props.ResponseInformation

            if hasattr(props, "ServerReference"):
                conn_ack_info.server_reference = props.ServerReference

        return conn_ack_info

    def __create_client(self, connection_params: ConnectionParams) -> mqtt.Client:
        """
        Create async PAHO MQTT 5 Client
        Parameters
        ----------
        connection_params - the connection parameters
        Returns async PAHO MQTT 5 Client
        """

        client = None

        # Clean Session is used only for the MQTT 3
        if self.__protocol == mqtt.MQTTv311:
            client = mqtt.Client(
                connection_params.client_id, protocol=self.__protocol, clean_session=connection_params.clean_session
            )
        else:
            client = mqtt.Client(connection_params.client_id, protocol=self.__protocol)

        if (
            (connection_params.ca_cert is not None)
            and (connection_params.cert is not None)
            and (connection_params.key is not None)
        ):
            ca_path = self.__temp_files_manager.create_new_temp_file(connection_params.ca_cert)
            cert_path = self.__temp_files_manager.create_new_temp_file(connection_params.cert)
            key_path = self.__temp_files_manager.create_new_temp_file(connection_params.key)
            client.tls_set(ca_certs=ca_path, certfile=cert_path, keyfile=key_path)

        return client
