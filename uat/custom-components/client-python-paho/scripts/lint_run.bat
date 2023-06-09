@REM -------------------------------
@REM Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
@REM SPDX-License-Identifier: Apache-2.0
@REM -------------------------------

@echo off
set PYTHONPATH=%~dp0..\src\
set PYLINTRC=%~dp0..\src\.pylintrc
pylint %~dp0..\src\
flake8 --append-config %~dp0..\src\.flake8 %~dp0..\src\
black --check --line-length 120 --exclude="dev-env|grpc_client_server/grpc_generated" %~dp0..\src\
