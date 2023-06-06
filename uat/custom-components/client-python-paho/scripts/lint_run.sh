#!/bin/bash

#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

DIR=$(realpath "$(dirname "${BASH_SOURCE[0]}")")
export PYTHONPATH=$DIR/../src/
export PYLINTRC=$DIR/../src/.pylintrc
pylint $DIR/../src/
flake8 --append-config $DIR/../src/.flake8 $DIR/../src/
black --check --line-length 79 --exclude="dev-env|grpc_client_server/grpc_generated" $DIR/../src/
