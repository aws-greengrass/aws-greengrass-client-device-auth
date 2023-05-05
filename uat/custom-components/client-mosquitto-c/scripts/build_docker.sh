#!/bin/sh

cp -a ../../proto proto

docker build -f Dockerfile -t client-mosquitto-c .

# docker run -it --rm --name=client-mosquitto-c --mount type=bind,source=${PWD},target=/src client-mosquitto-c bash
docker run -it --rm --name=client-mosquitto-c client-mosquitto-c bash

