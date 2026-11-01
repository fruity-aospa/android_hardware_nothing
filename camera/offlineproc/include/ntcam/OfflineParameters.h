/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <binder/IMemory.h>
#include <binder/Parcelable.h>
#include <camera/CameraMetadata.h>
#include <cutils/native_handle.h>

#include <vector>

namespace android {
namespace hardware {

/*
 * Field order and types reproduce the Nothing OS layout (0x88 bytes): the
 * prebuilt libofflineproc_jni.so allocates and destroys this object inline.
 */
class OfflineParameters : public Parcelable {
  public:
    OfflineParameters();
    OfflineParameters(std::vector<native_handle*> inputs, std::vector<native_handle*> outputs,
                      std::vector<int> inputInfo, std::vector<int> outputInfo);
    explicit OfflineParameters(CameraMetadata& metadata);
    explicit OfflineParameters(sp<IMemory>& memory);
    virtual ~OfflineParameters() = default;

    status_t writeToParcel(Parcel* parcel) const override;
    status_t readFromParcel(const Parcel* parcel) override;

  private:
    std::vector<int> mInputInfo;
    std::vector<int> mOutputInfo;
    std::vector<native_handle*> mInputs;
    std::vector<native_handle*> mOutputs;
    CameraMetadata mMetadata;
    sp<IMemory> mMemory;
};

static_assert(sizeof(OfflineParameters) == 0x88, "OfflineParameters must match the Nothing OS layout");

}  // namespace hardware
}  // namespace android
