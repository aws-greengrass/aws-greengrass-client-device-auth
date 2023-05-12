#!/bin/sh

rm -rf proto && cp -a ../../proto proto

docker build -f Dockerfile --target builder -t client-mosquitto-c:builder-amd64 .

# docker run -it --rm --name=client-mosquitto-c-builder client-mosquitto-c:builder-amd64 bash
