#
# Automatically generated file. DO NOT MODIFY
#

PRODUCT_SOONG_NAMESPACES += \
    vendor/nothing/camera

PRODUCT_COPY_FILES += \
    vendor/nothing/camera/proprietary/system/etc/sysconfig/nothing-hiddenapi-package-allowlist.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/nothing-hiddenapi-package-allowlist.xml \
    vendor/nothing/camera/proprietary/system_ext/etc/permissions/privapp-permissions-NTCamera.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-NTCamera.xml \
    vendor/nothing/camera/proprietary/vendor/etc/camera/NdotFont/Ndot-55.otf:$(TARGET_COPY_OUT_VENDOR)/etc/camera/NdotFont/Ndot-55.otf \
    vendor/nothing/camera/proprietary/vendor/etc/camera/Robotofont/Roboto-55.ttf:$(TARGET_COPY_OUT_VENDOR)/etc/camera/Robotofont/Roboto-55.ttf \
    vendor/nothing/camera/proprietary/vendor/etc/camera/SansCJKFont/NotoSansCJK.ttc:$(TARGET_COPY_OUT_VENDOR)/etc/camera/SansCJKFont/NotoSansCJK.ttc \
    vendor/nothing/camera/proprietary/vendor/etc/init/vendor.noth.hardware.camera-service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.noth.hardware.camera-service.rc

PRODUCT_PACKAGES += \
    vendor.noth.hardware.camera-V1-ndk \
    vendor.noth.hardware.camera-service-impl \
    libofflineproc_jni \
    libQnnHtpV73Stub \
    vendor.noth.hardware.camera-service.xml \
    vendor.noth.hardware.camera-service
