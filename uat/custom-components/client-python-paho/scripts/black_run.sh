#!/bin/bash

#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

DIR=$(realpath "$(dirname "$0")")
black --line-length 120 --verbose --exclude="dev-env|grpc_client_server/grpc_generated" $DIR\..\src
