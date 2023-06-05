"""GRPC Client helper test"""
# pylint: disable=no-member
from __future__ import print_function

import logging

import grpc
from grpc_client_server.grpc_generated import mqtt_client_control_pb2_grpc
from grpc_client_server.grpc_generated import mqtt_client_control_pb2

from exceptions.grpc_exception import GRPCException


def run():
    """GRPC CLient main"""
    # NOTE(gRPC Python Team): .close() is possible on a channel and should be
    # used in circumstances in which the with statement does not fit the needs
    # of the code.
    print("Will try to greet world ...")

    try:
        with grpc.insecure_channel("localhost:50051") as channel:
            stub = mqtt_client_control_pb2_grpc.MqttClientControlStub(channel)
            response = stub.ShutdownAgent(
                mqtt_client_control_pb2.ShutdownRequest(
                    reason="Just stop it, please"
                )
            )
            print("Answer received: " + str(response))
    except grpc.RpcError as error:
        print("Server is inactive")
        raise GRPCException(error) from error


if __name__ == "__main__":
    logging.basicConfig()
    run()
