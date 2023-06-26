#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""MQTT5 Connection."""
# pylint: disable=no-name-in-module
import asyncio
import logging
import socket
import time

from dataclasses import dataclass
from typing import List

import paho.mqtt.client as mqtt

from paho.mqtt import MQTTException as PahoException
from paho.mqtt.properties import Properties
from paho.mqtt.reasoncodes import ReasonCodes
from paho.mqtt.packettypes import PacketTypes
from paho.mqtt.subscribeoptions import SubscribeOptions

from mqtt_lib.temp_files_manager import TempFilesManager
from exceptions.mqtt_exception import MQTTException
from grpc_client_server.grpc_generated.mqtt_client_control_pb2 import (
    Mqtt5ConnAck,
    Mqtt5Disconnect,
    MqttPublishReply,
    MqttSubscribeReply,
    Mqtt5Message,
)
from grpc_client_server.grpc_discovery_client import GRPCDiscoveryClient


@dataclass
class MqttResult:
    """Class MqttResult"""

    reason_code: int = mqtt.MQTT_ERR_UNKNOWN
    flags: dict = None
    properties: Properties = None
    error_string: str = ""
    mid: int = 0


@dataclass
class SubscribesResult:
    """Class SubscribesResult"""

    granted_qos: List[int] = None
    properties: Properties = None
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

    # TODO Add user properties


@dataclass
class ConnectResult:  # pylint: disable=too-many-instance-attributes
    """Class container for connect results"""

    # Useful information from CONNACK packet. Can be missed
    connected: bool
    conn_ack_info: Mqtt5ConnAck
    error: str


@dataclass
class MqttPubMessage:
    """Publish message information and payload"""

    qos: int
    retain: bool
    topic: str
    payload: bytes

    # TODO add user properties and other fields


@dataclass
class Subscribtion:
    """Subscription information"""

    # Topic filter.
    filter: str
    # Maximum QoS
    qos: int
    # No local subscription.
    no_local: bool

    retain_as_published: bool
    retain_handling: int


class AsyncioHelper:
    """Class Asyncio helper for the Paho MQTT"""

    logger = logging.getLogger("AsyncioHelper")

    def __init__(self, loop, client):
        """Construct AsyncioHelper"""
        self.__logger = AsyncioHelper.logger
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

        self.__logger.debug("On socket open")
        self.loop.add_reader(sock, callback)
        self.misc = self.loop.create_task(self.misc_loop())

    def on_socket_close(self, client, userdata, sock):  # pylint: disable=unused-argument
        """Client socket close callback"""
        self.__logger.debug("On socket close")
        self.loop.remove_reader(sock)
        self.misc.cancel()

    def on_socket_register_write(self, client, userdata, sock):  # pylint: disable=unused-argument
        """Client socket register write"""
        self.__logger.debug("On socket register write")

        def callback():
            client.loop_write()

        self.loop.add_writer(sock, callback)

    def on_socket_unregister_write(self, client, userdata, sock):  # pylint: disable=unused-argument
        """Client socket unregister write"""
        self.__logger.debug("On socket unregister write")
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
        self.__grpc_client = grpc_client
        self.__connection_id = 0
        self.__on_connect_future = None
        self.__on_disconnect_future = None
        self.__on_publish_future = None
        self.__on_subscribe_future = None
        self.__asyncio_helper = None  # pylint: disable=unused-private-member
        self.__loop = asyncio.get_running_loop()

    def set_connection_id(self, connection_id: int):
        """
        Connection id setter
        Parameters
        ----------
        connection_id - connection id as assigned by MQTT library
        """
        self.__connection_id = connection_id

    async def __connect_helper(self, host, port, keep_alive, clean_start):
        """MQTT Connect async wrapper."""
        self.__client.connect(
            host=host,
            port=port,
            keepalive=keep_alive,
            clean_start=clean_start,
        )

    async def start(self, timeout: int) -> ConnectResult:
        """
        Starts MQTT connection.
        Parameters
        ----------
        timeout - connect operation timeout in seconds
        Returns connection result
        """
        # TODO add properties
        self.__asyncio_helper = AsyncioHelper(self.__loop, self.__client)  # pylint: disable=unused-private-member

        clean_start = self.__connection_params.clean_session

        # Clean Start is used only for the MQTT 5
        if self.__protocol == mqtt.MQTTv311:
            clean_start = mqtt.MQTT_CLEAN_START_FIRST_ONLY

        self.__logger.info("MQTT connection ID %i connecting...", self.__connection_id)

        try:
            self.__on_connect_future = self.__loop.create_future()
            start_time = int(time.time())

            connect_awaitable = self.__connect_helper(
                host=self.__connection_params.host,
                port=self.__connection_params.port,
                keep_alive=self.__connection_params.keep_alive,
                clean_start=clean_start,
            )

            await asyncio.wait_for(connect_awaitable, timeout)

            passed_time = int(time.time()) - start_time
            remaining_timeout = max(timeout - passed_time, 0)

            result = await asyncio.wait_for(self.__on_connect_future, remaining_timeout)
            conn_ack_info = MqttConnection.convert_to_conn_ack(result)

            connected = True
            if result.reason_code == mqtt.MQTT_ERR_SUCCESS:
                self.__logger.info(
                    "MQTT connection ID %s connected, client id %s",
                    self.__connection_id,
                    self.__connection_params.client_id,
                )
            else:
                connected = False
                self.__logger.info(
                    "MQTT connection ID %s failed with error: %s", self.__connection_id, result.error_string
                )

            return ConnectResult(connected=connected, conn_ack_info=conn_ack_info, error=result.error_string)
        except asyncio.TimeoutError:
            self.__logger.exception("Exception occurred during connect")
            return ConnectResult(connected=False, conn_ack_info=None, error="Connect timeout error")
        except (PahoException, socket.timeout) as error:
            self.__logger.exception("Exception occurred during connect")
            return ConnectResult(connected=False, conn_ack_info=None, error=str(error))
        except Exception as error:
            self.__logger.exception("Exception occurred during connect")
            raise MQTTException from error
        finally:
            self.__on_connect_future = None

    def __on_connect(
        self, client, userdata, flags, reason_code, properties=None
    ):  # pylint: disable=unused-argument,too-many-arguments
        """
        Paho MQTT connect callback
        """
        mqtt_reason_code = reason_code
        error_string = None

        if hasattr(reason_code, "value"):
            mqtt_reason_code = reason_code.value
            error_string = str(reason_code)
        else:
            error_string = mqtt.error_string(reason_code)

        mqtt_result = MqttResult(
            reason_code=mqtt_reason_code, flags=flags, properties=properties, error_string=error_string
        )
        try:
            if self.__on_connect_future is not None:
                self.__on_connect_future.set_result(mqtt_result)
        except asyncio.InvalidStateError:
            pass

    async def disconnect(self, timeout: int, reason: int):
        """
        Closes MQTT connection.
        Parameters
        ----------
        timeout - disconnect operation timeout in seconds
        reason - disconnect reason code
        """
        self.__logger.info("Disconnect MQTT connection with reason code %i", reason)
        self.__on_disconnect_future = self.__loop.create_future()

        try:
            if self.__protocol == mqtt.MQTTv5:
                reason_code_obj = ReasonCodes(PacketTypes.DISCONNECT, identifier=reason)
                self.__client.disconnect(reasoncode=reason_code_obj)
            else:
                self.__client.disconnect()

            result = await asyncio.wait_for(self.__on_disconnect_future, timeout)
        except asyncio.TimeoutError as error:
            raise MQTTException("Couldn't disconnect from MQTT broker") from error
        finally:
            self.__on_disconnect_future = None

        if result.reason_code != mqtt.MQTT_ERR_SUCCESS:
            raise MQTTException(f"Couldn't disconnect from MQTT broker - rc {result.reason_code}")

    def __on_disconnect(self, client, userdata, reason_code, properties=None):  # pylint: disable=unused-argument
        """
        Paho MQTT disconnect callback
        """
        mqtt_reason_code = reason_code

        if hasattr(reason_code, "value"):
            mqtt_reason_code = reason_code.value

        mqtt_result = MqttResult(reason_code=mqtt_reason_code, properties=properties)

        disconnect_info = MqttConnection.convert_to_disconnect(mqtt_result=mqtt_result)
        self.__grpc_client.on_mqtt_disconnect(self.__connection_id, disconnect_info, None)

        try:
            if self.__on_disconnect_future is not None:
                self.__on_disconnect_future.set_result(mqtt_result)
        except asyncio.InvalidStateError:
            pass

    async def publish(self, timeout: int, message: MqttPubMessage) -> MqttPublishReply:
        """
        Publish MQTT message.
        Parameters
        ----------
        timeout - publish operation timeout in seconds
        message - Mqtt message info and payload
        Returns MqttPublishReply object
        """
        # TODO add set property
        self.state_check()
        self.__on_publish_future = self.__loop.create_future()

        try:
            publish_info = self.__client.publish(
                topic=message.topic, payload=message.payload, qos=message.qos, retain=message.retain
            )

            result = await asyncio.wait_for(self.__on_publish_future, timeout)

            if publish_info.rc != mqtt.MQTT_ERR_SUCCESS:
                self.__logger.error(
                    "MQTT Publish failed with code %i: %s", publish_info.rc, mqtt.error_string(publish_info.rc)
                )
                raise MQTTException(f"Couldn't publish {publish_info.rc}")

            self.__logger.debug(
                "Published on topic '%s' QoS %i retain %i with rc %i message id %i",
                message.topic,
                message.qos,
                message.retain,
                publish_info.rc,
                result.mid,
            )

            return MqttConnection.convert_to_pub_reply(publish_info)
        except asyncio.TimeoutError as error:
            raise MQTTException("Couldn't publish") from error
        finally:
            self.__on_publish_future = None

    def __on_publish(self, client, userdata, mid):  # pylint: disable=unused-argument
        """
        Paho MQTT publish callback
        """
        mqtt_result = MqttResult(mid=mid)

        try:
            if self.__on_publish_future is not None:
                self.__on_publish_future.set_result(mqtt_result)
        except asyncio.InvalidStateError:
            pass

    async def subscribe(
        self, timeout: int, subscriptions: List[Subscribtion]
    ) -> MqttSubscribeReply:  # TODO Add user properties
        """
        Subscribe on MQTT topics.
        Parameters
        ----------
        timeout - subscribe operation timeout in seconds
        subscriptions - Subscriptions information
        Returns MqttSubscribeReply object
        """
        # TODO add set property
        self.state_check()

        mqtt_subscriptions = []

        filters = []
        qos = []
        no_local = []
        retain_as_published = []
        retain_handling = []
        for sub in subscriptions:
            if self.__protocol == mqtt.MQTTv5:
                options = SubscribeOptions(
                    qos=sub.qos,
                    noLocal=sub.no_local,
                    retainAsPublished=sub.retain_as_published,
                    retainHandling=sub.retain_handling,
                )
                mqtt_subscriptions.append((sub.filter, options))

                retain_as_published.append(str(sub.retain_as_published))
                retain_handling.append(str(sub.retain_handling))
                no_local.append(str(sub.no_local))
            else:
                mqtt_subscriptions.append((sub.filter, sub.qos))

            filters.append(sub.filter)
            qos.append(str(sub.qos))

        self.__on_subscribe_future = self.__loop.create_future()

        try:
            (resp_code, message_id) = self.__client.subscribe(mqtt_subscriptions, properties=None)

            result = await asyncio.wait_for(self.__on_subscribe_future, timeout)

            if resp_code != mqtt.MQTT_ERR_SUCCESS:
                self.__logger.error("MQTT Subscribe failed with code %i: %s", resp_code, mqtt.error_string(resp_code))
                raise MQTTException(f"Couldn't publish {resp_code}")

            self.__logger.debug(
                "Subscribed on filters '%s' with QoS %s no local %s retain as published %s"
                " retain handing %s with message id %i",
                ",".join(filters),
                ",".join(qos),
                ",".join(no_local),
                ",".join(retain_as_published),
                ",".join(retain_handling),
                message_id,
            )

            return MqttConnection.convert_to_sub_reply(result)
        except asyncio.TimeoutError as error:
            raise MQTTException("Couldn't subscribe") from error
        finally:
            self.__on_subscribe_future = None

    def __on_subscribe(
        self, client, userdata, mid, reason_codes, properties=None
    ):  # pylint: disable=unused-argument,too-many-arguments
        """
        Paho MQTT subscribe callback
        """
        if self.__protocol == mqtt.MQTTv5:
            granted_qos = []
            for reason_code in reason_codes:
                granted_qos.append(reason_code.value)
            mqtt_result = SubscribesResult(mid=mid, granted_qos=granted_qos, properties=properties)
        else:
            mqtt_result = SubscribesResult(mid=mid, granted_qos=reason_codes, properties=properties)

        try:
            if self.__on_subscribe_future is not None:
                self.__on_subscribe_future.set_result(mqtt_result)
        except asyncio.InvalidStateError:
            pass

    def __on_message(self, client, userdata, message):  # pylint: disable=unused-argument
        """
        Paho MQTT receive message callback
        """
        self.__logger.debug("onMessage message %s from topic '%s'", message.payload, message.topic)
        mqtt_message = MqttConnection.convert_to_mqtt_message(message=message)
        self.__grpc_client.on_receive_mqtt_message(self.__connection_id, mqtt_message)

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
            protocol_version_for_log = "3.1.1"
            client = mqtt.Client(
                connection_params.client_id, protocol=self.__protocol, clean_session=connection_params.clean_session
            )
        else:
            protocol_version_for_log = "5"
            client = mqtt.Client(connection_params.client_id, protocol=self.__protocol)

        tls_for_log = "without TLS"

        if (
            (connection_params.ca_cert is not None)
            and (connection_params.cert is not None)
            and (connection_params.key is not None)
        ):
            tls_for_log = "with TLS"
            ca_path = self.__temp_files_manager.create_new_temp_file(connection_params.ca_cert)
            cert_path = self.__temp_files_manager.create_new_temp_file(connection_params.cert)
            key_path = self.__temp_files_manager.create_new_temp_file(connection_params.key)
            client.tls_set(ca_certs=ca_path, certfile=cert_path, keyfile=key_path)

        client.on_connect = self.__on_connect
        client.on_disconnect = self.__on_disconnect
        client.on_publish = self.__on_publish
        client.on_subscribe = self.__on_subscribe
        client.on_message = self.__on_message

        self.__logger.info("Creating MQTT %s client %s", protocol_version_for_log, tls_for_log)

        return client

    def state_check(self):
        """Check if MQTT is connected"""
        if (self.__client is None) or not self.__client.is_connected():
            raise MQTTException("MQTT client is not in connected state")

    @staticmethod
    def convert_to_conn_ack(mqtt_result: MqttResult) -> Mqtt5ConnAck:
        """
        Convert MqttResult info to Mqtt5ConnAck
        Parameters
        ----------
        mqtt_result - MqttResult object
        Returns Mqtt5ConnAck object
        """
        props = mqtt_result.properties
        conn_ack = Mqtt5ConnAck(
            sessionPresent=mqtt_result.flags["session present"],
            reasonCode=mqtt_result.reason_code,
            sessionExpiryInterval=getattr(props, "SessionExpiryInterval", None),
            receiveMaximum=getattr(props, "ReceiveMaximum", None),
            maximumQoS=getattr(props, "MaximumQoS", None),
            retainAvailable=getattr(props, "RetainAvailable", None),
            maximumPacketSize=getattr(props, "MaximumPacketSize", None),
            assignedClientId=getattr(props, "AssignedClientIdentifier", None),
            reasonString=getattr(props, "ReasonString", None),
            wildcardSubscriptionsAvailable=getattr(props, "WildcardSubscriptionAvailable", None),
            subscriptionIdentifiersAvailable=getattr(props, "SubscriptionIdentifierAvailable", None),
            sharedSubscriptionsAvailable=getattr(props, "SharedSubscriptionAvailable", None),
            serverKeepAlive=getattr(props, "ServerKeepAlive", None),
            responseInformation=getattr(props, "ResponseInformation", None),
            serverReference=getattr(props, "ServerReference", None),
            topicAliasMaximum=getattr(props, "TopicAliasMaximum", None),
        )
        # TODO add user properties

        return conn_ack

    @staticmethod
    def convert_to_disconnect(mqtt_result: MqttResult) -> Mqtt5ConnAck:
        """
        Convert MqttResult info to Mqtt5Disconnect
        Parameters
        ----------
        mqtt_result - MqttResult object
        Returns Mqtt5Disconnect object
        """
        props = mqtt_result.properties
        disconnect_info = Mqtt5Disconnect(
            reasonCode=mqtt_result.reason_code,
            sessionExpiryInterval=getattr(props, "SessionExpiryInterval", None),
            reasonString=getattr(props, "ReasonString", None),
            serverReference=getattr(props, "ServerReference", None),
        )
        # TODO add user properties
        return disconnect_info

    @staticmethod
    def convert_to_pub_reply(pub_info: mqtt.MQTTMessageInfo) -> MqttPublishReply:
        """
        Build MQTT publish reply
        Parameters
        ----------
        pub_info - published message info
        Returns MqttPublishReply object
        """
        # Python Paho MQTT does not support recieving any properties for the pusblished messages
        return MqttPublishReply(reasonCode=pub_info.rc)

    @staticmethod
    def convert_to_sub_reply(mqtt_result: SubscribesResult) -> MqttSubscribeReply:
        """
        Build MQTT subscribe reply
        Parameters
        ----------
        mqtt_result - subscribe info
        Returns MqttSubscribeReply object
        """
        # TODO add user properties handling
        mqtt_sub_reply = MqttSubscribeReply(
            reasonCodes=mqtt_result.granted_qos, reasonString=getattr(mqtt_result.properties, "ReasonString", None)
        )

        return mqtt_sub_reply

    @staticmethod
    def convert_to_mqtt_message(message: mqtt.MQTTMessage) -> Mqtt5Message:
        """
        Convert MQTTMessage info to Mqtt5Message
        Parameters
        ----------
        message - MQTTMessage object
        Returns Mqtt5Message object
        """
        mqtt_message = Mqtt5Message(
            topic=message.topic, payload=message.payload, qos=message.qos, retain=message.retain
        )

        # TODO add user properties, payload format indicator, content type for mqtt5
        return mqtt_message
