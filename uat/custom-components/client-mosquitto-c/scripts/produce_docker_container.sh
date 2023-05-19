#!/bin/sh

set -e

rm -rf proto && cp -a ../../proto proto

docker build -f Dockerfile --target builder -t client-mosquitto-c:builder-amd64 .
docker build -f Dockerfile --target runner -t client-mosquitto-c:runner-amd64 .
docker save client-mosquitto-c:runner-amd64 | gzip > mosquitto-test-client.amd64.tar.gz
