@REM -------------------------------
@REM Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
@REM SPDX-License-Identifier: Apache-2.0
@REM -------------------------------

@echo off
set start_dir=%cd%
cd %~dp0../src
pip install virtualenv
python -m venv dev-env
call dev-env\Scripts\activate.bat
pip install pyinstaller
pip install -r requirements.txt
python -m grpc_tools.protoc -I../../../proto --python_out=../src/grpc_client_server/grpc_generated --pyi_out=../src/grpc_client_server/grpc_generated --grpc_python_out=../src/grpc_client_server/grpc_generated ../../../proto/mqtt_client_control.proto
python fix_generated.py
pyinstaller client-python-paho.spec
move dist\client-python-paho.exe ..\
rmdir /s /q dist
rmdir /s /q build
cd %start_dir%
