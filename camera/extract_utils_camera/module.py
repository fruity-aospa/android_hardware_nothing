#
# SPDX-FileCopyrightText: Paranoid Android
# SPDX-License-Identifier: Apache-2.0
#

from os import path
from typing import List, Optional

from extract_utils.fixups_blob import blob_fixups_user_type
from extract_utils.fixups_lib import lib_fixups_user_type
from extract_utils.module import ExtractUtilsModule
from extract_utils.utils import remove_dir_contents

CAMERA_REL_PATH = path.join('hardware', 'nothing', 'camera')


class ExtractUtilsCameraModule(ExtractUtilsModule):
    def __init__(
        self,
        blob_fixups: Optional[blob_fixups_user_type] = None,
        lib_fixups: Optional[lib_fixups_user_type] = None,
        namespace_imports: Optional[List[str]] = None,
    ):
        super().__init__(
            device='camera',
            vendor='nothing',
            device_rel_path=CAMERA_REL_PATH,
            blob_fixups=blob_fixups,
            lib_fixups=lib_fixups,
            namespace_imports=namespace_imports,
        )

        # Blobs live next to the scripts instead of under vendor/
        self.vendor_rel_path = CAMERA_REL_PATH
        self.vendor_path = self.device_path
        self.vendor_rro_path = path.join(self.vendor_path, 'rro_overlays')

    def cleanup(self):
        for proprietary_file in self.proprietary_files:
            vendor_path = self.proprietary_file_vendor_path(proprietary_file)
            remove_dir_contents(vendor_path)
