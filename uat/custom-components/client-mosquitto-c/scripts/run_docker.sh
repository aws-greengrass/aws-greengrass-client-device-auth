#!/bin/sh
#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

docker build -f Dockerfile --target runner -t client-mosquitto-c:runner-amd64 .

# docker run -it --rm --name=client-mosquitto-c-runner client-mosquitto-c:runner-amd64  mosquitto-docker 47619 172.17.0.1
