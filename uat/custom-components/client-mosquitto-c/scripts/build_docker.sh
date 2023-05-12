#!/bin/sh

rm -rf proto && cp -a ../../proto proto

docker build -f Dockerfile -t client-mosquitto-c .

docker run -it --rm --name=client-mosquitto-c client-mosquitto-c bash
