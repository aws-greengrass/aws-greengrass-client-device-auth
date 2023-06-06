#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""Logger setup helpers."""
import logging


def setup_log():
    """Function sets logger formating."""
    logging.basicConfig(
        format="%(name)s: [%(levelname)s] %(message)s", level=logging.DEBUG
    )
