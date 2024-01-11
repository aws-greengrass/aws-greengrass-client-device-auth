#!/usr/bin/env bash
#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

set -e

# install CDA locally
cd ./..
mvn -B -ntp clean install -DskipTests
cd -

# build benchmark project
mvn -B -ntp clean package

# run all benchmarks
java -jar target/benchmarks.jar -rf json
