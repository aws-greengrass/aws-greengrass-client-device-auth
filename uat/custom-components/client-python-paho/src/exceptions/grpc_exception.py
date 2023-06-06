# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0.

"""GRPCException"""

from exceptions.client_exception import ClientException


class GRPCException(ClientException):
    """GRPCException class"""
