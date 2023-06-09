@REM -------------------------------
@REM Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
@REM SPDX-License-Identifier: Apache-2.0
@REM -------------------------------

@echo off
python -m grpc_tools.protoc -I%~dp0../../../proto --python_out=%~dp0../src/grpc_client_server/grpc_generated --pyi_out=%~dp0../src/grpc_client_server/grpc_generated --grpc_python_out=%~dp0../src/grpc_client_server/grpc_generated %~dp0../../../proto/mqtt_client_control.proto
