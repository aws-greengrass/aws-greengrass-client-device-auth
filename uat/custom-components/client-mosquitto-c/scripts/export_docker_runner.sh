#!/bin/sh

docker save client-mosquitto-c:runner-amd64 | gzip > mosquitto-test-client.amd64.tar.gz
