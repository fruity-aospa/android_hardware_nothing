/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

#include <ntcam/OfflineParameters.h>

#include <binder/Parcel.h>

#include <utility>

namespace android {
namespace hardware {

OfflineParameters::OfflineParameters() = default;

OfflineParameters::OfflineParameters(std::vector<native_handle*> inputs,
                                     std::vector<native_handle*> outputs,
                                     std::vector<int> inputInfo, std::vector<int> outputInfo)
    : mInputInfo(std::move(inputInfo)),
      mOutputInfo(std::move(outputInfo)),
      mInputs(std::move(inputs)),
      mOutputs(std::move(outputs)) {}

OfflineParameters::OfflineParameters(CameraMetadata& metadata) : mMetadata(metadata) {}

OfflineParameters::OfflineParameters(sp<IMemory>& memory) : mMemory(memory) {}

// Buffers are owned by the caller and only described here; no service in this
// tree imports them, so the handles themselves are not marshalled.
status_t OfflineParameters::writeToParcel(Parcel* parcel) const {
    status_t err = parcel->writeInt32Vector(mInputInfo);
    if (err != OK) return err;
    err = parcel->writeInt32Vector(mOutputInfo);
    if (err != OK) return err;
    err = parcel->writeInt32(static_cast<int32_t>(mInputs.size()));
    if (err != OK) return err;
    return parcel->writeInt32(static_cast<int32_t>(mOutputs.size()));
}

status_t OfflineParameters::readFromParcel(const Parcel* parcel) {
    status_t err = parcel->readInt32Vector(&mInputInfo);
    if (err != OK) return err;
    err = parcel->readInt32Vector(&mOutputInfo);
    if (err != OK) return err;
    int32_t inputs = 0, outputs = 0;
    err = parcel->readInt32(&inputs);
    if (err != OK) return err;
    return parcel->readInt32(&outputs);
}

}  // namespace hardware
}  // namespace android
