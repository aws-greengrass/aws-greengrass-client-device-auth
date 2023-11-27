#!/usr/bin/env bash
#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#
set -e
jjtree -OUTPUT_DIRECTORY=src/main/java/com/aws/greengrass/clientdevices/auth/configuration/parser src/main/javacc/RuleExpression.jjt
mvn license:format
