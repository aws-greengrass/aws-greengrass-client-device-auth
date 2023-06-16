#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#

"""Manager for working with temp files"""

import tempfile
import os


class TempFilesManager:
    """Manager for working with temp files"""

    def __init__(self):
        """Construct TempFilesManager"""
        self.__temp_files = []

    def create_new_temp_file(self, data: str) -> str:
        """
        Create new temp file
        Parameters:
        ----------
        Returns new temp file path in system
        """
        new_file = tempfile.NamedTemporaryFile(mode="wt", delete=False)  # pylint: disable=consider-using-with

        try:
            new_file.write(data)
        finally:
            new_file.close()

        self.__temp_files.append(new_file.name)

        return new_file.name

    def destroy_all_files(self):
        """Destroy all files"""
        for file in self.__temp_files:
            os.unlink(file)
