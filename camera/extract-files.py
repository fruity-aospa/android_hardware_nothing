#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: Paranoid Android
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import blob_fixup, blob_fixups_user_type
from extract_utils.fixups_lib import lib_fixups
from extract_utils.main import ExtractUtils
from extract_utils_camera.module import ExtractUtilsCameraModule


blob_fixups: blob_fixups_user_type = {
    'vendor/etc/init/vendor.noth.hardware.camera-service.rc': blob_fixup()
        .regex_replace(r'\n\s*task_profiles NtCamAlgoCapacity', ''),
    'vendor/lib64/vendor.noth.hardware.camera-service-impl.so': blob_fixup()
        .add_needed('libui_shim.so'),
}  # fmt: skip


module = ExtractUtilsCameraModule(
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=[
        'vendor/nothing/asteroids',
    ],
)

if __name__ == '__main__':
    ExtractUtils.device(module).run()
