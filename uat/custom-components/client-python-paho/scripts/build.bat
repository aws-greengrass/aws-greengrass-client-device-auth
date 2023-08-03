@REM -------------------------------
@REM Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
@REM SPDX-License-Identifier: Apache-2.0
@REM -------------------------------

@echo off
set start_dir=%cd%
cd %~dp0../src
pip install virtualenv || (cd %start_dir% & exit /b 1)
python -m venv dev-env || (cd %start_dir% & exit /b 2)
call dev-env\Scripts\activate.bat || (cd %start_dir% & exit /b 3)
pip install pyinstaller || (cd %start_dir% & deactivate & exit /b 4)
pip install -r requirements.txt || (cd %start_dir% & deactivate & exit /b 5)

python -m grpc_tools.protoc -I../../../proto --python_out=../src/grpc_client_server/grpc_generated ^
--pyi_out=../src/grpc_client_server/grpc_generated --grpc_python_out=../src/grpc_client_server/grpc_generated ^
../../../proto/mqtt_client_control.proto || (cd %start_dir% & deactivate & exit /b 6)

python fix_generated.py || (cd %start_dir% & deactivate & exit /b 7)
pyinstaller client-python-paho.spec || (cd %start_dir% & deactivate & rmdir /s /q dist & rmdir /s /q build & exit /b 8)
move dist\client-python-paho.exe ..\ || (cd %start_dir% & deactivate & rmdir /s /q dist & rmdir /s /q build & exit /b 9)
rmdir /s /q dist || (cd %start_dir% & deactivate & rmdir /s /q build & exit /b 10)
rmdir /s /q build || (cd %start_dir% & deactivate & exit /b 11)
cd %start_dir% || (deactivate & exit /b 12)
deactivate || exit /b 13
