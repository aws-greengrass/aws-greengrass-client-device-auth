#!/usr/bin/env bash
#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

set -e

# install CDA locally
cd ./..
mvn clean install -DskipTests
cd -

# build benchmark project
mvn clean package

# run all benchmarks
java -jar target/benchmarks.jar
