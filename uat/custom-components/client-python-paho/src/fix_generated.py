# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0.

"""Update generated files import correct work of generated code"""
import os
import fileinput

print(os.listdir(os.path.join(os.path.dirname(os.path.realpath(__file__)))))

with fileinput.FileInput(
    os.path.join(
        os.path.dirname(os.path.realpath(__file__)),
        "grpc_client_server/grpc_generated/mqtt_client_control_pb2_grpc.py",
    ),
    inplace=True,
) as file:
    for line in file:
        print(
            line.replace(
                "import mqtt_client_control_pb2",
                "import grpc_client_server."
                "grpc_generated.mqtt_client_control_pb2",
            ),
            end="",
        )
