/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "ntcam_offlineproc"

#include <android/hardware/BnOfflineProcService.h>
#include <binder/IPCThreadState.h>
#include <binder/LazyServiceRegistrar.h>
#include <binder/ProcessState.h>
#include <log/log.h>

#include <cerrno>

using android::sp;
using android::String16;
using android::binder::Status;
using android::hardware::BnOfflineProcService;
using android::hardware::IOfflineProcClient;
using android::hardware::OfflineParameters;
using android::hardware::camera2::impl::CameraMetadataNative;

namespace {

/*
 * Nothing OS hosts this service in cameraserver and backs it with
 * /vendor/lib64/libofflinepostprocclient.so. Phone (3a) firmware does not ship
 * that library, so on stock every session fails initOffline() with -EINVAL and
 * NTCamera keeps processing inside the camera HAL. Reproduce exactly that.
 */
class OfflineProcService : public BnOfflineProcService {
  public:
    Status initOffline(int32_t, int32_t, int32_t, int32_t, int32_t, int32_t, int32_t) override {
        return Status::fromServiceSpecificError(-EINVAL, "offline post-processing is unavailable");
    }

    Status setPath(const String16&) override { return Status::ok(); }

    Status addMetadata(int32_t, const CameraMetadataNative&) override { return Status::ok(); }

    Status setOfflineListener(const sp<IOfflineProcClient>& listener) override {
        mListener = listener;
        return Status::ok();
    }

    Status processOffline(const OfflineParameters&) override { return Status::ok(); }

    Status setBitmapData(const OfflineParameters&) override { return Status::ok(); }

    Status closeOffline() override {
        mListener.clear();
        return Status::ok();
    }

  private:
    sp<IOfflineProcClient> mListener;
};

}  // namespace

int main() {
    android::ProcessState::self()->setThreadPoolMaxThreadCount(0);

    android::status_t status = android::binder::LazyServiceRegistrar::getInstance().registerService(
            sp<OfflineProcService>::make(), "media.offline");
    if (status != android::OK) {
        ALOGE("Failed to register media.offline: %d", status);
        return 1;
    }

    android::IPCThreadState::self()->joinThreadPool();
    return 1;
}
