#!/bin/sh
#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

rm -rf build && mkdir -p build

CXXFLAGS="-Wall -Wextra -g -O0" cmake -Bbuild -H.
cmake --build build -j `nproc` --target all
