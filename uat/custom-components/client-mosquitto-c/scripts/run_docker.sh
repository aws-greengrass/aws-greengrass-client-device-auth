#!/bin/sh

docker build -f Dockerfile --target runner -t client-mosquitto-c:runner .

# docker run -it --rm --name=client-mosquitto-c-runner client-mosquitto-c:runner mosquitto-docker 47619 172.17.0.1
