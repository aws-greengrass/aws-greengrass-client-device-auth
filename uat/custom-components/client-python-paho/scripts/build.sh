#!/bin/bash

#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

set -e

STARTDIR=$(realpath "$(dirname "$0")")
cd $STARTDIR/../src
pip3 install virtualenv
python3 -m venv dev-env
. ./dev-env/bin/activate
pip3 install pyinstaller
pip3 install -r requirements.txt
python3 -m grpc_tools.protoc -I../../../proto --python_out=../src/grpc_client_server/grpc_generated --pyi_out=../src/grpc_client_server/grpc_generated --grpc_python_out=../src/grpc_client_server/grpc_generated ../../../proto/mqtt_client_control.proto
python3 fix_generated.py
pyinstaller client-python-paho.spec
mv dist/client-python-paho ../
rm -r dist
rm -r build
cd $STARTDIR
