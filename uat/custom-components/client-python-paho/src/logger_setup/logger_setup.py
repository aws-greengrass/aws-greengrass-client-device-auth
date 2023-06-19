#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""Logger setup helpers."""
import logging


def setup_log():
    """Function sets logger formating."""
    logging.basicConfig(
        format="[%(levelname)-5s] %(asctime)s.%(msecs)03d %(name)s - %(message)s",
        level=logging.INFO,
        datefmt="%Y-%m-%d %H:%M:%S",
    )
