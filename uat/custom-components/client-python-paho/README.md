# Test MQTT5/311 client bases on Paho for Python

The controlled `test MQTT v5.0/v3.1.1 client` is based on Paho Python is used to test Greengrass v2 MQTT v5.0 compatibility.

## Install Requirements

Python 3.8+ has to be used.

### Windows

Install python https://www.python.org/downloads/

### Linux

```sh
sudo apt-get install -y python3 python3-venv python3-pip
```

## Setup

Install python requirements

```cmd
pip install -r src/requirements.txt
```

Install linting and checking requirements

```cmd
pip install -r src/requirements_lint.txt
```

## Run Client

### Generate gRPC files

Windows

```cmd
scripts\generate_grpc.bat
```

Linux

```sh
./scripts/generate_grpc.sh
```

### Arguments
Currently that client support three arguments and only first is mandatory.

#### Agent Id
Arbitrary string which will identify that instance of client in Control.

#### IP of control
The IP address where gRPC server of Control is listening.
Value 127.0.0.1 will be used by default.

#### Port of control
The TCP port where gRPC server of Control is listening.
Value 47619 will be used by default.

### Run

```cmd
python src/main.py paho-python-agent 47619 127.0.0.1
```

### Run linting and checking

Windows

```cmd
scripts\lint_run.bat
```

Linux

```sh
./scripts/lint_run.sh
```

### Run Black formatter

Windows

```cmd
scripts\black_run.bat
```

Linux

```sh
./scripts/black_run.sh
```

## Build with maven

For both Windows and Linux

```sh
mvn clean license:check package
```
The executable will apear in the client-python-paho directory

Windows - "client-python-paho.exe"

Run

```cmd
client-python-paho.exe paho-python-agent 47619 127.0.0.1
```

Linux - "client-python-paho"

Run

```sh
./client-python-paho paho-python-agent 47619 127.0.0.1
```

## Limitations

Python Paho MQTT library is limited in

1. Publishing
Receiving properties for the PUBACK is not implemented - Python Paho MQTT publish() method and on_publish() callback do not provide it.

2. Subscribing
Python Paho MQTT does not provide the API to set the Subscription ID in SUBSCRIBE request.

3. Unsubscription
In Python Paho on_unsubscribe() callback does not provide reason codes for MQTTv3, instead zero code will be returned on success.
