#!/bin/sh

docker save client-mosquitto-c:runner | gzip > mosquitto-test-client.amd64.tar.gz
