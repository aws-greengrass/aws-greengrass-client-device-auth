# mosquitto test client

MQTT 3.1.1/5.0 client for tests based on C mosquitto library

## Install requirements for native build
```bash
sudo apt-get install -y build-essential gcc cmake git autoconf libtool pkg-config libmosquitto-dev
```
Note: required version 2.0 or above of mosquitto

## Install requirements for docker build
```bash
sudo apt-get install -y docker.io
```
## Native build
```bash
CXXFLAGS="-Wall -Wextra -g -O0" cmake -Bbuild -H.
cmake --build build -j `nproc` --target all
```

## Docker buid
```bash
scripts/produce_docker_container.sh
```

## Native Run
```bash
build/src/mosquitto-test-client agent-mosquitto 47619 127.0.0.1
```

## Docker run
```bash
docker run --rm --name=client-mosquitto-c client-mosquitto-c:runner-amd64 agent-mosquitto 47619 172.17.0.1 127.0.0.1
```

## Description
TODO: update
Controlled MQTT v3.1.1/5.0 client based on mosquitto library.

## Installation
No need to be installed, expecting will deployed as Greengrass component.

## Usage
That client accept below arguments
agent-id - id string for the control, mandatory
port     - port of the control
IPs      - IP addresses of the control

## License
Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
SPDX-License-Identifier: Apache-2.0


## Limitations
Mosquitto library is limited in

1. In asynchrony.
Not all calls are asynchronous.

2. Subscription on multiple filters.
Mosquitto API mosquitto_subscribe_multiple() have a common QoS and other properties like retain handling, no local and retain as published for all topic filters.
It violates MQTT v5.0 where topic filters have separate QoS and other properties.
At the same time usage mosquitto_subscribe_v5() in a loop can break logic of subscription id which is common for all filters of one subscribe request.
In result we check all values of QoS and properties in gRPC SubscribeMqtt request and if are not the same report an error in that client.

3. Unsubscription
In Mosquitto API mosquitto_unsubscribe_v5_callback_set() callback does not provides result code, instead zero code will be returned on success.

4. Windows implementation
mosquitto library can build on Windows but miss threaded interface and can works only in synchronous mode. That brokes logic of gRPC requests and client control.
