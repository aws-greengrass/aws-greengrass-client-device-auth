@REM -------------------------------
@REM Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
@REM SPDX-License-Identifier: Apache-2.0
@REM -------------------------------

@echo off
black --line-length 120 --verbose --exclude="dev-env|grpc_client_server/grpc_generated" %~dp0\..\src
