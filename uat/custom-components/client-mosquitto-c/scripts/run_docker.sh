#!/bin/sh

docker build -f Dockerfile --target runner -t client-mosquitto-c:runner-amd64 .

# docker run -it --rm --name=client-mosquitto-c-runner client-mosquitto-c:runner-amd64  mosquitto-docker 47619 172.17.0.1
