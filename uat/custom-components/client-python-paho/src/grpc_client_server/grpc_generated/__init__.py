#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""Add generated directory to path for correct work of generated code"""
import sys
import os

sys.path.append(os.path.dirname(os.path.realpath(__file__)))
