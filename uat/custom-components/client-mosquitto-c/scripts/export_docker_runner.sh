#!/bin/sh
#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

docker save client-mosquitto-c:runner-amd64 | gzip > mosquitto-test-client.amd64.tar.gz
