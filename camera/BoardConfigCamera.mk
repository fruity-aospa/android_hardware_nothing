#
# SPDX-FileCopyrightText: Paranoid Android
# SPDX-License-Identifier: Apache-2.0
#

NOTHING_CAMERA_PATH := hardware/nothing/camera

DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE += \
    $(NOTHING_CAMERA_PATH)/configs/framework_compatibility_matrix.xml

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(NOTHING_CAMERA_PATH)/sepolicy/private
BOARD_VENDOR_SEPOLICY_DIRS += $(NOTHING_CAMERA_PATH)/sepolicy/vendor
