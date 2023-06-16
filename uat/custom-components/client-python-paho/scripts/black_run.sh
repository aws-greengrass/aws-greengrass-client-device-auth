#!/bin/bash

#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

DIR=$(realpath "$(dirname "$0")")
black $DIR\..\src
